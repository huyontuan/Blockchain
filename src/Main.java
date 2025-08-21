//Name: On Tuan Huy
//sID: s4028018

import java.util.*;

//   Minimal CLI for interacting with the blockchain:
//   - send <from> <to> <amount>
//   - mine <minerAddress>
//   - balance <address>
//   - show-chain
//   - exit

//   Persists to blockchain.json on exit.
public class Main {
    public static void main(String[] args) throws Exception {
        String filename = "blockchain.json";
        Blockchain blockchain = Blockchain.loadBlockchain(filename);
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n Java Blockchain CLI \n");
        System.out.println("Commands:");
        System.out.println("- send <sender> <receiver> <amount>");
        System.out.println("- mine <minerAddress>");
        System.out.println("- balance <address>");
        System.out.println("- show-chain");
        System.out.println("- exit");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            String[] parts = input.split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) continue;

            try {
                switch (parts[0]) {
                    case "send": {
                        if (parts.length != 4) { System.out.println("Usage: send <sender> <receiver> <amount>"); break; }
                        String sender = parts[1];
                        String receiver = parts[2];
                        double amount = Double.parseDouble(parts[3]);
                        Transaction tx = blockchain.createSpend(sender, receiver, amount);
                        boolean ok = blockchain.addTransaction(tx);
                        System.out.println(ok ? "Transaction added: " + tx.transactionId : "Transaction rejected");
                        break;
                    }
                    case "mine": {
                        if (parts.length != 2) { System.out.println("Usage: mine <minerAddress>"); break; }
                        String miner = parts[1];
                        blockchain.minePendingTransactions(miner);
                        System.out.println("Mined a block.");
                        break;
                    }
                    case "show-chain": {
                        blockchain.printChain();
                        break;
                    }
                    case "balance": {
                        if (parts.length != 2) { System.out.println("Usage: balance <address>"); break; }
                        String address = parts[1];
                        double bal = blockchain.checkBalance(address);
                        System.out.println("Balance of " + address + ": " + bal);
                        break;
                    }
                    case "exit": {
                        blockchain.saveBlockchain(filename);
                        System.out.println("Blockchain saved. Exiting...");
                        scanner.close();
                        return;
                    }
                    default:
                        System.out.println("Invalid command.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}