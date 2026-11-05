package fr.fnations.ui.pages;

import fr.fnations.server.FnServer;
import fr.fnations.ui.UiManager;

/** Enregistrement de toutes les pages de la tablette. */
public final class Pages {
    private Pages() {}

    public static void register(FnServer s, UiManager ui) {
        ui.register(new HomePage(s));
        ui.register(new NationPage(s));
        ui.register(new BankPage(s));
        ui.register(new CompanyPage(s));
        ui.register(new JobsPage(s));
        ui.register(new MarketPage(s));
        ui.register(new ResearchPage(s));
        ui.register(new WarPage(s));
        ui.register(new RoomPage(s));
        ui.register(new StationPage(s));
        ui.register(new RankingPage(s));
        ui.register(new BoutiquePage(s));
        ui.register(new LootboxPage(s));
        ui.register(new SpawnerPage(s));
        ui.register(new WorldMapPage(s));
        ui.register(new NotationPage(s));
    }
}
