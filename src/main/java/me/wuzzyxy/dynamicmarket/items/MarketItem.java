package me.wuzzyxy.dynamicmarket.items;

public class MarketItem {
    private final String name;
    private double basePrice;
    private double minPrice;
    private int boughtAmount;
    private int soldAmount;

    public MarketItem(String name, double basePrice, int boughtAmount, int soldAmount, double minPrice) {
        this.name = name;
        this.basePrice = basePrice;
        this.boughtAmount = boughtAmount;
        this.soldAmount = soldAmount;
        this.minPrice = minPrice;
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

    public int getBoughtAmount() {
        return boughtAmount;
    }

    public int getSoldAmount() {
        return soldAmount;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    public void setBoughtAmount(int boughtAmount) {
        this.boughtAmount = boughtAmount;
    }

    public void setSoldAmount(int soldAmount) {
        this.soldAmount = soldAmount;
    }
}
