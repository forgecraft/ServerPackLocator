package cpw.mods.forge.serverpacklocator.secure;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import cpw.mods.forge.serverpacklocator.utils.NonceUtils;
import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Launcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.net.Proxy;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileKeyPairBasedSecurityManager implements IConnectionSecurityManager<Object>
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ProfileKeyPairBasedSecurityManager INSTANCE = new ProfileKeyPairBasedSecurityManager();
    private static final UUID DEFAULT_NILL_UUID = new UUID(0L, 0L);

    private final Map<UUID, String> currentChallenges = new ConcurrentHashMap<>();

    public static ProfileKeyPairBasedSecurityManager getInstance()
    {
        return INSTANCE;
    }

    private final SigningHandler signingHandler;
    private final UUID sessionId;
    private final SignatureValidator validator;

    private String challengePayload = "";

    private ProfileKeyPairBasedSecurityManager()
    {
        signingHandler = getSigningHandler();
        sessionId = getSessionId();
        validator = getSignatureValidator();
    }

    private static ArgumentHandler getArgumentHandler() {
        try {
            final Field argumentHandlerField = Launcher.class.getDeclaredField("argumentHandler");
            argumentHandlerField.setAccessible(true);
            return (ArgumentHandler) argumentHandlerField.get(Launcher.INSTANCE);
        }
        catch (NoSuchFieldException | IllegalAccessException | ClassCastException e)
        {
            throw new RuntimeException("Failed to get the argument handler used to start the system", e);
        }
    }

    private static String[] getLaunchArguments() {
        final ArgumentHandler argumentHandler = getArgumentHandler();
        try {
            final Field argsArrayField = ArgumentHandler.class.getDeclaredField("args");
            argsArrayField.setAccessible(true);
            return (String[]) argsArrayField.get(argumentHandler);
        }
        catch (NoSuchFieldException | IllegalAccessException | ClassCastException e)
        {
            throw new RuntimeException("Failed to get the launch arguments used to start the system", e);
        }
    }

    private static String getAccessToken() {
        final String[] arguments = getLaunchArguments();
        for (int i = 0; i < arguments.length; i++)
        {
            final String arg = arguments[i];
            if (Objects.equals(arg, "--accessToken")) {
                return arguments[i+1];
            }
        }

        return "";
    }

    private static UUID getSessionId() {
        final String[] arguments = getLaunchArguments();
        for (int i = 0; i < arguments.length; i++)
        {
            final String arg = arguments[i];
            if (Objects.equals(arg, "--uuid")) {
                return UUID.fromString(arguments[i+1].replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            }
        }

        return DEFAULT_NILL_UUID;
    }

    private static YggdrasilAuthenticationService getAuthenticationService() {
        return new YggdrasilAuthenticationService(Proxy.NO_PROXY); //For now, we do not support custom proxies.
    }

    private static UserApiService getApiService() {
        final String accessToken = getAccessToken();
        final YggdrasilAuthenticationService authenticationService = getAuthenticationService();
        if (accessToken.isBlank())
            return UserApiService.OFFLINE;

        return authenticationService.createUserApiService(accessToken);
    }

    private static KeyPairResponse getKeyPair() {
        final UserApiService apiService = getApiService();
        return apiService.getKeyPair();
    }

    private static ProfileKeyPair getProfileKeyPair() {
        final KeyPairResponse keyPairResponse = getKeyPair();
        if (keyPairResponse == null)
            return null;

        return new ProfileKeyPair(Crypt.stringToPemRsaPrivateKey(keyPairResponse.keyPair().privateKey()),
                new PublicKeyData(
                Crypt.stringToRsaPublicKey(keyPairResponse.keyPair().publicKey()),
                Instant.parse(keyPairResponse.expiresAt()),
                keyPairResponse.publicKeySignature().array()));
    }

    private static SigningHandler getSigningHandler() {
        final ProfileKeyPair profileKeyPair = getProfileKeyPair();
        if (profileKeyPair == null)
            return null;

        return new SigningHandler(profileKeyPair);
    }

    private static SignatureValidator getSignatureValidator() {
        final YggdrasilAuthenticationService authenticationService = getAuthenticationService();

        final ServicesKeySet keySet = authenticationService.getServicesKeySet();
        if (keySet == null || keySet.keys(ServicesKeyType.PROFILE_KEY).isEmpty())
            return SignatureValidator.ALWAYS_FAIL;

        return SignatureValidator.from(keySet, ServicesKeyType.PROFILE_KEY);
    }

    private static void validatePublicKey(PublicKeyData keyData, UUID sessionId, SignatureValidator systemValidator) throws Exception
    {
        if (keyData.key() == null) {
            throw new Exception("Missing public key!");
        } else {
            if (keyData.expiresAt().isBefore(Instant.now())) {
                throw new Exception("Public key has expired!");
            }
            if (!keyData.verifySessionId(systemValidator, sessionId)) {
                throw new Exception("Invalid public key!");
            }
        }
    }

    private static byte[] digest(final UUID target)
    {
        final byte[] payload = new byte[16];
        ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).putLong(target.getMostSignificantBits()).putLong(target.getLeastSignificantBits());

        return digest(payload);
    }

    private static byte[] digest(final String target)
    {
        final byte[] sessionIdPayload = target.getBytes(StandardCharsets.UTF_8);

        return digest(sessionIdPayload);
    }

    private static byte[] digest(byte[] payload) {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(payload);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException("Failed to get SHA-256 message digest", e);
        }
    }

    private static String sign(final UUID payload, final Signer signer) {
        byte[] messageHash = digest(payload);

        return signDigest(messageHash, signer);
    }

    private static String sign(final String payload, final Signer signer) {
        byte[] messageHash = digest(payload);

        return signDigest(messageHash, signer);
    }

    private static String sign(final byte[] payload, final Signer signer) {
        byte[] messageHash = digest(payload);

        return signDigest(messageHash, signer);
    }

    private static String signDigest(byte[] messageHash, Signer signer) {
        final byte[] signedPayload = signer.sign(messageHash);
        return Base64.getEncoder().encodeToString(signedPayload);
    }

    private static boolean validate(final UUID target, final SignatureValidator validator, final byte[] signature) {
        return validator.validate(digest(target), signature);
    }

    private static boolean validate(final String target, final SignatureValidator validator, final byte[] signature) {
        return validator.validate(digest(target), signature);
    }

    private static boolean validate(final byte[] target, final SignatureValidator validator, final byte[] signature) {
        return validator.validate(digest(target), signature);
    }

    @Override
    public void onClientConnectionCreation(final URLConnection connection)
    {
        if (signingHandler == null || sessionId.compareTo(DEFAULT_NILL_UUID) == 0) {
            LOGGER.warn("No signing handler is available for the current session (Missing keypair). Stuff might not work since we can not sign the requests!");
            return;
        }

        connection.setRequestProperty("Authentication", "SignedId");
        connection.setRequestProperty("AuthenticationId", sessionId.toString());
        connection.setRequestProperty("AuthenticationSignature", sign(sessionId, signingHandler.signer()));
        connection.setRequestProperty("AuthenticationKey", Base64.getEncoder().encodeToString(Crypt.rsaPublicKeyToString(signingHandler.keyPair().publicKeyData().key()).getBytes(StandardCharsets.UTF_8)));
        connection.setRequestProperty("AuthenticationKeyExpire", Base64.getEncoder().encodeToString(signingHandler.keyPair().publicKeyData().expiresAt().toString().getBytes(StandardCharsets.UTF_8)));
        connection.setRequestProperty("AuthenticationKeyExpireDigest", sign(signingHandler.keyPair().publicKeyData().expiresAt().toString(), signingHandler.signer()));
        connection.setRequestProperty("AuthenticationKeySignature", Base64.getEncoder().encodeToString(signingHandler.keyPair().publicKeyData().publicKeySignature()));
    }

    @Override
    public void onAuthenticateComplete(String challengeString) {
        this.challengePayload = sign(challengeString, signingHandler.signer());
    }

    @Override
    public void authenticateConnection(URLConnection connection) {
        connection.setRequestProperty("ChallengeSignature", this.challengePayload);
    }

    @Override
    public boolean onServerConnectionRequest(ChannelHandlerContext ctx, final FullHttpRequest msg)
    {
        final var headers = msg.headers();
        final String authentication = headers.get("Authentication");
        if (!Objects.equals(authentication, "SignedId")) {
            LOGGER.warn("External client attempted login without proper authentication header setup!");
            return false;
        }

        final UUID sessionId = getSessionId(headers);
        if (sessionId == null) return false;

        final String authenticationSignature = headers.get("AuthenticationSignature");
        if (authenticationSignature == null) {
            LOGGER.warn("External client attempted login without signature!");
            return false;
        }
        final byte[] encryptedSessionHashPayload;
        try {
            encryptedSessionHashPayload = Base64.getDecoder().decode(authenticationSignature);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted to login with a signature which was not decode-able: " + authenticationSignature);
            return false;
        }

        final String publicKeyString = headers.get("AuthenticationKey");
        if (publicKeyString == null) {
            LOGGER.warn("External client attempted login without public key!");
            return false;
        }
        final String decodedPublicKey;
        try {
            decodedPublicKey = new String(Base64.getDecoder().decode(publicKeyString), StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted to login with a public key which was not decode-able: " + publicKeyString);
            return false;
        }
        final PublicKey publicKey;
        try {
            publicKey = Crypt.stringToRsaPublicKey(decodedPublicKey);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted to login with a public key which was not in RSA format: " + decodedPublicKey);
            return false;
        }

        final String authenticationExpire = headers.get("AuthenticationKeyExpire");
        if (authenticationExpire == null) {
            LOGGER.warn("External client attempted login without expire information!");
            return false;
        }
        final String decodedAuthenticationExpire;
        try {
            decodedAuthenticationExpire = new String(Base64.getDecoder().decode(authenticationExpire), StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted to login with expire information which was not decode-able: " + publicKeyString);
            return false;
        }
        final Instant expire;
        try {
            expire = Instant.parse(decodedAuthenticationExpire);
        } catch (DateTimeParseException e) {
            LOGGER.warn("External client attempted login without a validly formatted expire information: " + authenticationExpire);
            return false;
        }


        final String authenticationExpireDigest = headers.get("AuthenticationKeyExpireDigest");
        if (authenticationExpireDigest == null) {
            LOGGER.warn("External client attempted login without expire digest information!");
            return false;
        }
        final byte[] decodedAuthenticationExpireDigest;
        try {
            decodedAuthenticationExpireDigest = Base64.getDecoder().decode(authenticationExpireDigest);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted to login with expire information which was not decode-able: " + publicKeyString);
            return false;
        }

        final String authenticationKeySignature = headers.get("AuthenticationKeySignature");
        if (authenticationKeySignature == null) {
            LOGGER.warn("External client attempted login without a key signature!");
            return false;
        }
        final byte[] keySignature;
        try {
            keySignature = Base64.getDecoder().decode(authenticationKeySignature);
        } catch (Throwable throwable) {
            LOGGER.warn("External client attempted login with a key signature which was not decode-able: " + authenticationKeySignature);
            return false;
        }

        final PublicKeyData keyData = new PublicKeyData(
                publicKey,
                expire,
                keySignature
        );

        final boolean challengeValidationRequired;
        final byte[] challengeSignature;
        final String challenge;
        if (!Objects.equals(msg.uri(), "/authenticate")) {
            final String challengeSignatureHeader = headers.get("ChallengeSignature");
            if (challengeSignatureHeader == null) {
                LOGGER.warn("External client attempted login without a challenge signature!");
                return false;
            }
            try {
                challengeValidationRequired = true;
                challengeSignature = Base64.getDecoder().decode(challengeSignatureHeader);
            } catch (Throwable throwable) {
                LOGGER.warn("External client attempted login with a challenge signature which was not decode-able.");
                return false;
            }

            challenge = currentChallenges.get(sessionId);
            if (challenge == null) {
                LOGGER.warn("External client attempted login with a challenge signature but connection has no challenge: " + new String(challengeSignature, StandardCharsets.UTF_8));
                return false;
            }
        } else {
            challengeValidationRequired = false;
            challengeSignature = new byte[0];
            challenge = "";
        }

        try {
            validatePublicKey(keyData, sessionId, validator);
            if (!validate(sessionId, keyData.validator(), encryptedSessionHashPayload)) {
                LOGGER.warn("External client attempted login with an invalid signature!");
                return false;
            }
            if (!validate(decodedAuthenticationExpire, keyData.validator(), decodedAuthenticationExpireDigest)) {
                LOGGER.warn("External client attempted login with an invalid expire signature!");
                return false;
            }
            if (challengeValidationRequired && !validate(challenge, keyData.validator(), challengeSignature)) {
                LOGGER.warn("External client attempted login with an invalid challenge signature!");
                return false;
            }
            if (!WhitelistVerificationHelper.getInstance().isAllowed(sessionId)) {
                LOGGER.warn("External client attempted login with a session id which is not on the whitelist!");
                return false;
            }
            return true;
        }
        catch (Exception e)
        {
            LOGGER.warn("External client failed to authenticate.", e);
            return false;
        }
    }

    private static UUID getSessionId(HttpHeaders headers) {
        final String authenticationId = headers.get("AuthenticationId");
        if (authenticationId == null)
        {
            LOGGER.warn("External client attempted login without session id!");
            return null;
        }
        final UUID sessionId;
        try {
            sessionId = UUID.fromString(authenticationId);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("External client attempted login with invalid session id format: " + authenticationId);
            return null;
        }
        return sessionId;
    }

    @Override
    public void onServerResponse(ChannelHandlerContext ctx, FullHttpRequest msg, FullHttpResponse resp) {
        final String challenge = NonceUtils.createNonce();

        final UUID sessionId = getSessionId(msg.headers());
        if (sessionId == null) {
            return;
        }

        currentChallenges.put(sessionId, challenge);
        resp.headers().set("Challenge", Base64.getEncoder().encodeToString(challenge.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public boolean validateConfiguration(SidedPackHandler<?> handler, Object o) {
        final String uuid = LaunchEnvironmentHandler.INSTANCE.getUUID();
        if (uuid == null || uuid.isEmpty()) {
            // invalid UUID - probably offline mode. not supported
            LaunchEnvironmentHandler.INSTANCE.addProgressMessage("NO UUID found. Offline mode does not work. No server mods will be downloaded");
            LOGGER.error("There was not a valid UUID present in this client launch. You are probably playing offline mode. Trivially, there is nothing for us to do.");
            return false;
        }

        return true;
    }

    @Override
    public void initialize() {
        //No initialization needed.
    }

    public record PublicKeyData(PublicKey key, Instant expiresAt, byte[] publicKeySignature) {

        boolean verifySessionId(SignatureValidator validator, UUID sessionId) {
            return validator.validate(this.signedPayload(sessionId), this.publicKeySignature);
        }

        public SignatureValidator validator() {
            return SignatureValidator.from(key(), "SHA256withRSA");
        }

        private byte[] signedPayload(UUID sessionId) {
            byte[] keyPayload = this.key.getEncoded();
            byte[] idWithKeyResult = new byte[24 + keyPayload.length];
            ByteBuffer bytebuffer = ByteBuffer.wrap(idWithKeyResult).order(ByteOrder.BIG_ENDIAN);
            bytebuffer.putLong(sessionId.getMostSignificantBits()).putLong(sessionId.getLeastSignificantBits()).putLong(this.expiresAt.toEpochMilli()).put(keyPayload);
            return idWithKeyResult;
        }
    }

    public record ProfileKeyPair(PrivateKey privateKey, PublicKeyData publicKeyData) {
    }

    private record SigningHandler(ProfileKeyPair keyPair, Signer signer) {

        private SigningHandler(ProfileKeyPair keyPair)
        {
            this(keyPair, Signer.from(keyPair.privateKey(), "SHA256withRSA"));
        }
    }
}
