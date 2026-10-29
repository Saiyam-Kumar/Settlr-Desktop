
package com.settlr;

import java.time.LocalDateTime;

public class Settlement {

    private final int id;
    private final int groupId;
    private final int fromMemberId;
    private final int toMemberId;
    private final String fromName;
    private final String toName;
    private final double amount;
    private final LocalDateTime settledAt;

    public Settlement(
            int id,
            int groupId,
            int fromMemberId,
            int toMemberId,
            String fromName,
            String toName,
            double amount,
            LocalDateTime settledAt) {
        this.id = id;
        this.groupId = groupId;
        this.fromMemberId = fromMemberId;
        this.toMemberId = toMemberId;
        this.fromName = fromName;
        this.toName = toName;
        this.amount = amount;
        this.settledAt = settledAt;
    }

    public int getId() {
        return id;
    }

    public int getGroupId() {
        return groupId;
    }

    public int getFromMemberId() {
        return fromMemberId;
    }

    public int getToMemberId() {
        return toMemberId;
    }

    public String getFromName() {
        return fromName;
    }

    public String getToName() {
        return toName;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }
}
