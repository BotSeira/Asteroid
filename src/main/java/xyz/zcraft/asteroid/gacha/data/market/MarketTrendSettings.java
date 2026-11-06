package xyz.zcraft.asteroid.gacha.data.market;

/** Code-level data settings. ECharts appearance remains in the HTML template. */
public final class MarketTrendSettings {
    private MarketTrendSettings() {}

    // Price lines and candles independently divide the requested time range.
    public static final int PRICE_PERIODS = 20;
    public static final int CANDLE_PERIODS = 50;
    public enum PriceValue { FIRST, LAST, AVERAGE }
    public static final PriceValue PRICE_VALUE = PriceValue.LAST;

    // Equal-width distribution bins; quantities are summed, empty bins remain zero.
    public static final int DISTRIBUTION_BINS = 20;
    public static final double DISTRIBUTION_MARGIN = 1;

    public static final double CANDLE_PADDING_RATIO = 0.08;
    public static final double FLAT_CANDLE_PADDING_RATIO = 0.02;
    public static final double MIN_CANDLE_PADDING = 0.01;
}
