package cpw.mods.forge.serverpackutility.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import cpw.mods.forge.serverpacklocator.ModAccessor;
import net.neoforged.neoforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(BrandingControl.class)
public class BrandingControlMixin {

    @Shadow
    private static List<String> overCopyrightBrandings;

    @WrapOperation(
            method = "computeOverCopyrightBrandings",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList$Builder;build()Lcom/google/common/collect/ImmutableList;")
    )
    private static ImmutableList<String> onComputeOverCopyrightBrandings(ImmutableList.Builder<String> instance, Operation<ImmutableList<String>> original) {
        instance.add(ModAccessor.getStatusLine());
        return original.call(instance);
    }
}
