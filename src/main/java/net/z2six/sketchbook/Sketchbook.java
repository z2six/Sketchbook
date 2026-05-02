package net.z2six.sketchbook;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.z2six.sketchbook.book.SketchBookData;
import net.z2six.sketchbook.compat.curios.CuriosCompat;
import net.z2six.sketchbook.network.BookSketchPayload;
import net.z2six.sketchbook.network.BookSketchColorPayload;
import net.z2six.sketchbook.network.BookSketchRequestPayload;
import net.z2six.sketchbook.network.BookSketchSyncPayload;
import net.z2six.sketchbook.network.RememberScenePayload;
import net.z2six.sketchbook.network.RipSketchPagePayload;
import net.z2six.sketchbook.network.SceneMemoryListRequestPayload;
import net.z2six.sketchbook.network.SceneMemoryListSyncPayload;
import net.z2six.sketchbook.network.SketchReferenceRequestPayload;
import net.z2six.sketchbook.network.SketchReferenceSyncPayload;
import net.z2six.sketchbook.network.UseSceneMemoryPayload;

import java.util.UUID;

@Mod(Sketchbook.MODID)
public class Sketchbook {
    public static final String MODID = "sketchbook";

    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SketchBookData>> BOOK_SKETCHES = DATA_COMPONENTS.registerComponentType(
        "book_sketches",
        builder -> builder.persistent(SketchBookData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(SketchBookData.NETWORK_CODEC)).cacheEncoding()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PENCIL_CASE_CONTENTS = DATA_COMPONENTS.registerComponentType(
        "pencil_case_contents",
        builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).cacheEncoding()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> TORN_SKETCH_REFERENCE = DATA_COMPONENTS.registerComponentType(
        "torn_sketch_reference",
        builder -> builder.persistent(UUIDUtil.STRING_CODEC).networkSynchronized(ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString)).cacheEncoding()
    );
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SKETCHBOOK_TAB = CREATIVE_MODE_TABS.register(
        "sketchbook",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.sketchbook"))
            .icon(() -> new ItemStack(SketchbookItems.PENCIL_CASE.get()))
            .displayItems((parameters, output) -> SketchbookItems.acceptCreativeItems(output))
            .build()
    );

    public Sketchbook(IEventBus modEventBus, ModContainer modContainer) {
        DATA_COMPONENTS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        SketchbookItems.ITEMS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, SketchbookConfig.SERVER_SPEC);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::enqueueImc);
        modEventBus.addListener(SketchbookConfig::onLoad);
        modEventBus.addListener(SketchbookConfig::onReload);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(BookSketchPayload.TYPE, BookSketchPayload.STREAM_CODEC, BookSketchPayload::handle);
        registrar.playToServer(BookSketchColorPayload.TYPE, BookSketchColorPayload.STREAM_CODEC, BookSketchColorPayload::handle);
        registrar.playToServer(BookSketchRequestPayload.TYPE, BookSketchRequestPayload.STREAM_CODEC, BookSketchRequestPayload::handle);
        registrar.playToServer(RipSketchPagePayload.TYPE, RipSketchPagePayload.STREAM_CODEC, RipSketchPagePayload::handle);
        registrar.playToServer(RememberScenePayload.TYPE, RememberScenePayload.STREAM_CODEC, RememberScenePayload::handle);
        registrar.playToServer(SceneMemoryListRequestPayload.TYPE, SceneMemoryListRequestPayload.STREAM_CODEC, SceneMemoryListRequestPayload::handle);
        registrar.playToServer(SketchReferenceRequestPayload.TYPE, SketchReferenceRequestPayload.STREAM_CODEC, SketchReferenceRequestPayload::handle);
        registrar.playToServer(UseSceneMemoryPayload.TYPE, UseSceneMemoryPayload.STREAM_CODEC, UseSceneMemoryPayload::handle);
        registrar.playToClient(BookSketchSyncPayload.TYPE, BookSketchSyncPayload.STREAM_CODEC, BookSketchSyncPayload::handle);
        registrar.playToClient(SceneMemoryListSyncPayload.TYPE, SceneMemoryListSyncPayload.STREAM_CODEC, SceneMemoryListSyncPayload::handle);
        registrar.playToClient(SketchReferenceSyncPayload.TYPE, SketchReferenceSyncPayload.STREAM_CODEC, SketchReferenceSyncPayload::handle);
    }

    private void enqueueImc(InterModEnqueueEvent event) {
        CuriosCompat.enqueueImc();
    }
}
