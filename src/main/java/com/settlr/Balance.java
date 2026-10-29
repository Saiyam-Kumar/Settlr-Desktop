
package com.settlr;

public class Balance {

    private final int memberId;
    private final String name;
    private final double paid;
    private final double share;
    private final double settlementsSent;
    private final double settlementsReceived;

    public Balance(
            int memberId,
            String name,
            double paid,
            double share,
            double settlementsSent,
            double settlementsReceived) {
        this.memberId = memberId;
        this.name = name;
        this.paid = paid;
        this.share = share;
        this.settlementsSent = settlementsSent;
        this.settlementsReceived = settlementsReceived;
    }

    // Backward-compatible constructor for existing code
    public Balance(int memberId, String name, double paid, double share) {
        this(memberId, name, paid, share, 0, 0);
    }

    public int getMemberId() {
        return memberId;
    }

    public String getName() {
        return name;
    }

    public double getPaid() {
        return paid;
    }

    public double getShare() {
        return share;
    }

    public double getSettlementsSent() {
        return settlementsSent;
    }

    public double getSettlementsReceived() {
        return settlementsReceived;
    }

    // Positive = should receive; negative = owes
    public double getBalance() {
        return Math.round(
                (paid - share + settlementsSent - settlementsReceived) * 100.0
        ) / 100.0;
    }
}
