package me.wuzzyxy.dynamicmarket.items;

public class MarketItem implements Cloneable{
    public static final double DEFAULT_HALF_LIFE_HOURS = 48;

    private final String name;
    private double basePrice;
    private double impactK;
    private double minPrice;
    private long boughtAmount;
    private long soldAmount;

    private String category = "misc";

    private double net;
    private long lastDecay;
    private double halfLifeHours = DEFAULT_HALF_LIFE_HOURS;

    public MarketItem(String name, double basePrice, long boughtAmount, long soldAmount, double minPrice, double impactK) {
        this.name = name;
        this.basePrice = basePrice;
        this.boughtAmount = boughtAmount;
        this.soldAmount = soldAmount;
        this.minPrice = minPrice;
        this.impactK = impactK;

        this.net = boughtAmount - soldAmount;
        this.lastDecay = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public double getMinPrice() {
        return minPrice;
    }

    /***
     * Lifetime tallies. Analytics and the history trigger only — the price stopped
     * being a function of these when net started decaying.
     */
    public long getBoughtAmount() {
        return boughtAmount;
    }

    public long getSoldAmount() {
        return soldAmount;
    }

    /***
     * Net units the market is long, pulled back toward zero as it ages. The tallies on
     * their own are a one way ratchet: price becomes a function of every trade that ever
     * happened, so an item that got dumped once sits on the floor for the rest of the
     * server's life. Settling on read means no scheduler and nothing to catch up after
     * a restart.
     */
    public double getNet() {
        long now = System.currentTimeMillis();
        if (halfLifeHours > 0 && now > lastDecay) {
            net *= Math.pow(0.5, (now - lastDecay) / (halfLifeHours * 3_600_000.0));
        }
        lastDecay = now;
        return net;
    }

    /***
     * Log-price move per net unit. ln2 / units_to_double.
     */
    public double getK() {
        return impactK;
    }

    public double getHalfLifeHours() {
        return halfLifeHours;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category == null || category.isBlank() ? "misc" : category;
    }

    public long getLastDecay() {
        return lastDecay;
    }

    public void recordBuy(int amount) {
        getNet();
        net += amount;
        boughtAmount += amount;
    }

    public void recordSell(int amount) {
        getNet();
        net -= amount;
        soldAmount += amount;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }
    public void setK(double impactK) {
        this.impactK = impactK;
    }

    public void setMinPrice(double minPrice) {
        this.minPrice = minPrice;
    }

    public void setHalfLifeHours(double halfLifeHours) {
        this.halfLifeHours = halfLifeHours;
    }

    /***
     * Puts back what was on disk. A row written before the decay columns existed has
     * last_decay 0, and decaying from 1970 would wipe the position.
     */
    public void restoreNet(double net, long lastDecay) {
        this.net = net;
        this.lastDecay = lastDecay > 0 ? lastDecay : System.currentTimeMillis();
    }

    /***
     * Admin override. Moves the price too, otherwise /dmarket set only rewrites the
     * analytics and leaves the market where it was.
     */
    public void setCounters(long boughtAmount, long soldAmount) {
        this.boughtAmount = boughtAmount;
        this.soldAmount = soldAmount;
        this.net = boughtAmount - soldAmount;
        this.lastDecay = System.currentTimeMillis();
    }

    public String toString() {
        return "MarketItem{" +
                "name='" + name + '\'' +
                ", basePrice=" + basePrice +
                ", impactK=" + impactK +
                ", minPrice=" + minPrice +
                ", boughtAmount=" + boughtAmount +
                ", soldAmount=" + soldAmount +
                ", net=" + net +
                '}';
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MarketItem item = (MarketItem) obj;
        return name.equals(item.name);
    }

    public MarketItem clone() {
        try {
            return (MarketItem) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

}
