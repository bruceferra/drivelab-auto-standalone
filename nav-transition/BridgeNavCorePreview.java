package ar.com.rango5gps.drivelabauto.boot;

import ar.com.rango5gps.drivelabnav.core.NavMosaicLayout;

/**
 * Read-only NAV core integration for the v24 Bridge runtime overlay.
 * The historical Bridge v24 snapshot is NEVER modified.
 * This class never starts or moves Android tasks or changes vehicle state.
 */
final class BridgeNavCorePreview {
    private BridgeNavCorePreview() {}

    static String describe(int width, int height, float density,
                           int safeLeft, int safeTop, int safeRight, int safeBottom,
                           int primaryPercent) {
        if(width<240 || height<240 || density<0.5f)
            return "Resolución no disponible. No se aplica distribución.";
        StringBuilder out=new StringBuilder();
        out.append("Pantalla ").append(width).append("×").append(height)
           .append(" · densidad ").append(density).append("\n");
        for(NavMosaicLayout.Mode mode : new NavMosaicLayout.Mode[]{
            NavMosaicLayout.Mode.SINGLE, NavMosaicLayout.Mode.DUAL,
            NavMosaicLayout.Mode.MEDIA_RAIL, NavMosaicLayout.Mode.MAIN_STACK,
            NavMosaicLayout.Mode.THREE_COLUMNS}) {
            try {
                NavMosaicLayout.Plan plan=NavMosaicLayout.calculate(
                    new NavMosaicLayout.Request(width,height,safeLeft,safeTop,
                        safeRight,safeBottom,density,mode,primaryPercent,false));
                out.append("\n").append(mode.name())
                   .append(" · ").append(plan.axis.name()).append(": ");
                for(int i=0;i<plan.tiles.size();i++) {
                    NavMosaicLayout.Rect r=plan.tiles.get(i);
                    if(i>0)out.append(" | ");
                    out.append(r.width()).append("×").append(r.height());
                }
            } catch (IllegalArgumentException | IllegalStateException rejected) {
                out.append("\n").append(mode.name()).append(": no viable");
            }
        }
        return out.toString();
    }
}
