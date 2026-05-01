package net.z2six.sketchbook;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.z2six.sketchbook.book.SketchBookData;
import net.z2six.sketchbook.network.BookSketchPayload;
import net.z2six.sketchbook.network.BookSketchSyncPayload;

@Mod(Sketchbook.MODID)
public class Sketchbook {
    public static final String MODID = "sketchbook";

    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SketchBookData>> BOOK_SKETCHES = DATA_COMPONENTS.registerComponentType(
        "book_sketches",
        builder -> builder.persistent(SketchBookData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(SketchBookData.CODEC)).cacheEncoding()
    );

    public Sketchbook(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
        SketchbookItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::buildCreativeTabs);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(BookSketchPayload.TYPE, BookSketchPayload.STREAM_CODEC, BookSketchPayload::handle);
        registrar.playToClient(BookSketchSyncPayload.TYPE, BookSketchSyncPayload.STREAM_CODEC, BookSketchSyncPayload::handle);
    }

    private void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SketchbookItems.PENCIL.get());
        }
    }
}
