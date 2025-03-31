package net.forgecraft.serverpackutility.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.realmsclient.client.RealmsClient;
import net.forgecraft.serverpacklocator.ModAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Unique
    private Minecraft serverpacklocator$getCurrentInstance() {
        return (Minecraft) (Object) this;
    }

    @WrapMethod(method = "buildInitialScreens")
    private Runnable serverpacklocator$buildInitialScreens(@Nullable Minecraft.GameLoadCookie cookie, Operation<Runnable> operation) {
        // parse server address
        var serverAddress = ServerAddress.parseString(ModAccessor.getQuickPlayServer());

        if ((cookie == null || !cookie.quickPlayData().isEnabled()) && !serverAddress.getHost().equals("server.invalid")) {
            RealmsClient realmsclient = RealmsClient.getOrCreate(serverpacklocator$getCurrentInstance());
            cookie = new Minecraft.GameLoadCookie(realmsclient, new GameConfig.QuickPlayData(
                null,
                null,
                serverAddress.toString(),
                null
            ));
        }

        return operation.call(cookie);
    }
}
