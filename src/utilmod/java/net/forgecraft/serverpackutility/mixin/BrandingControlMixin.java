package net.forgecraft.serverpackutility.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.forgecraft.serverpacklocator.ModAccessor;
import net.neoforged.neoforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BrandingControl.class)
public class BrandingControlMixin {

    @WrapOperation(
            method = "computeOverCopyrightBrandings",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList$Builder;build()Lcom/google/common/collect/ImmutableList;")
    )
    private static ImmutableList<String> onComputeOverCopyrightBrandings(ImmutableList.Builder<String> instance, Operation<ImmutableList<String>> original) {
        instance.add(ModAccessor.getStatusLine());
        return original.call(instance);
    }
}
