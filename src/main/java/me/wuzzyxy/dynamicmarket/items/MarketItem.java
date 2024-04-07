package me.wuzzyxy.dynamicmarket.items;

public class MarketItem implements Cloneable{
    private final String name;
    private double basePrice;
    private double percentage;
    private double minPrice;
    private int boughtAmount;
    private int soldAmount;

    public MarketItem(String name, double basePrice, int boughtAmount, int soldAmount, double minPrice, double percentage) {
        this.name = name;
        this.basePrice = basePrice;
        this.boughtAmount = boughtAmount;
        this.soldAmount = soldAmount;
        this.minPrice = minPrice;
        this.percentage = percentage;
    }

    public String getName() {
        return name;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public double getPercentage() {
        return percentage;
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
    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    public void setMinPrice(double minPrice) {
        this.minPrice = minPrice;
    }

    public void setBoughtAmount(int boughtAmount) {
        this.boughtAmount = boughtAmount;
    }

    public void setSoldAmount(int soldAmount) {
        this.soldAmount = soldAmount;
    }

    public String toString() {
        return "MarketItem{" +
                "name='" + name + '\'' +
                ", basePrice=" + basePrice +
                ", percentage=" + percentage +
                ", minPrice=" + minPrice +
                ", boughtAmount=" + boughtAmount +
                ", soldAmount=" + soldAmount +
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
