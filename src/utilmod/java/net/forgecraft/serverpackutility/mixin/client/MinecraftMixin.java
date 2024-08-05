package net.forgecraft.serverpackutility.mixin.client;

import net.forgecraft.serverpacklocator.ModAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(
            method = "lambda$buildInitialScreens$9",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject(@Nullable Minecraft.GameLoadCookie cookie, CallbackInfo ci) {
        // quit out if user requested quick play via vanilla client (launch args)
        if (cookie != null && cookie.quickPlayData().isEnabled())
            return;

        // parse server address
        var serverAddress = ServerAddress.parseString(ModAccessor.getQuickPlayServer());

        // quit out if invalid server address passed
        if (serverAddress.getHost().equals("server.invalid"))
            return;

        // connect to server
        ConnectScreen.startConnecting(
                new JoinMultiplayerScreen(new TitleScreen()),
                (Minecraft) (Object) this,
                serverAddress,
                new ServerData("ServerPackLocator - QuickPlay Server", serverAddress.toString(), ServerData.Type.OTHER),
                true,
                null
        );

        // cancel vanilla code
        ci.cancel();
    }
}
