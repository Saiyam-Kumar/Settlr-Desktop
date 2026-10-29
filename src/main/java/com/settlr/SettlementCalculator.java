package com.settlr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Turns member balances into a small set of "who pays whom" transfers (exact, in cents). */
public final class SettlementCalculator {

    public record Transfer(int fromId, int toId, String fromName, String toName, double amount) {
    }

    private static final class Person {
        final int id;
        final String name;
        long cents;

        Person(int id, String name, long cents) {
            this.id = id;
            this.name = name;
            this.cents = cents;
        }
    }

    private SettlementCalculator() {
    }

    public static List<Transfer> suggest(List<Balance> balances) {
        List<Person> debtors = new ArrayList<>();
        List<Person> creditors = new ArrayList<>();

        for (Balance b : balances) {
            long cents = Math.round(b.getBalance() * 100);
            if (cents < 0) {
                debtors.add(new Person(b.getMemberId(), b.getName(), -cents));
            } else if (cents > 0) {
                creditors.add(new Person(b.getMemberId(), b.getName(), cents));
            }
        }

        Comparator<Person> largestFirst = (a, b) -> Long.compare(b.cents, a.cents);
        debtors.sort(largestFirst);
        creditors.sort(largestFirst);

        List<Transfer> result = new ArrayList<>();
        int d = 0;
        int c = 0;
        while (d < debtors.size() && c < creditors.size()) {
            Person debtor = debtors.get(d);
            Person creditor = creditors.get(c);
            long pay = Math.min(debtor.cents, creditor.cents);

            result.add(new Transfer(debtor.id, creditor.id, debtor.name, creditor.name, pay / 100.0));

            debtor.cents -= pay;
            creditor.cents -= pay;
            if (debtor.cents == 0) {
                d++;
            }
            if (creditor.cents == 0) {
                c++;
            }
        }
        return result;
    }
}