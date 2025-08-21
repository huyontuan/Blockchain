//Name: On Tuan Huy
//sID: s4028018

import java.util.*;
import java.io.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

//   Blockchain manages:
//   - The chain (list of blocks)
//   - A mempool (pending transactions)
//   - Mining parameters (difficulty, reward)
//   - A UTXO set for validation and balance calculation
//  
//   Features:
//   - Genesis block creation with a coinbase to "genesis"
//   - Transaction validation via UTXOSet before adding to mempool
//   - Mining: prepend coinbase, validate txs in order, build block, PoW, and apply
//   - Persistence via JSON (Gson)
//   - Balance checking by scanning a reconstructed UTXO snapshot
//   - Chain validation (hash linkage and PoW rule)
public class Blockchain {

    public List<Block> chain;
    public List<Transaction> pendingTransactions;
    public int difficulty = 3;
    public double miningReward = 10.0;

    private final UTXOSet utxo = new UTXOSet();

    //   Initialize a new blockchain with a genesis block and build UTXO from it.
    public Blockchain() {
        chain = new ArrayList<>();
        pendingTransactions = new ArrayList<>();
        chain.add(createGenesisBlock());
        // Build UTXO from genesis
        utxo.rebuildFromChain(chain);
    }

    //   Create a simple genesis block.
    //   - Adds a single coinbase to "genesis" with 1000.0 units.
    //   - For real chains, genesis is fixed and hardcoded.
    private Block createGenesisBlock() {
        // Simple genesis: single coinbase to "genesis" address with large supply or 0 outputs
        List<Transaction> genesisTxs = new ArrayList<>();
        // Seed some funds to a known address
        Transaction coin = Transaction.coinbase("genesis", 1000.0);
        genesisTxs.add(coin);
        Block genesis = new Block(0, genesisTxs, "0");
        return genesis;
    }

    //   Get the most recently added block.
    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    //   Add a regular transaction to the mempool if valid under current UTXO rules.
    //   Also reserves inputs to prevent double-spend conflicts among pending txs.
    public boolean addTransaction(Transaction tx) {
        if (!utxo.validateTransaction(tx)) {
            return false;
        }
        pendingTransactions.add(tx);
        utxo.reserveInputs(tx);
        return true;
    }

    //   Helper to construct a spend transaction from a sender to a receiver for amount.
    //   - Greedy selection of sender's UTXOs from a snapshot built by scanning the chain.
    //   - Adds change back to sender if needed.

    //   Throws IllegalArgumentException if insufficient funds.
    public Transaction createSpend(String sender, String receiver, double amount) {
        List<String> keys = new ArrayList<>();
        Map<String, Transaction.TxOutput> snapshot = utxoSnapshot();
        double accumulated = 0.0;
        List<Transaction.TxInput> inputs = new ArrayList<>();

        for (Map.Entry<String, Transaction.TxOutput> e : snapshot.entrySet()) {
            String key = e.getKey(); // txId:index
            Transaction.TxOutput out = e.getValue();
            if (!out.receiver.equals(sender)) continue;
            if (accumulated >= amount) break;
            String[] parts = key.split(":");
            String prevTx = parts[0];
            int index = Integer.parseInt(parts[1]);
            inputs.add(new Transaction.TxInput(prevTx, index, "sig")); // signature placeholder
            accumulated += out.amount;
            keys.add(key);
        }

        if (accumulated < amount) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        List<Transaction.TxOutput> outputs = new ArrayList<>();
        outputs.add(new Transaction.TxOutput(receiver, amount));
        double change = accumulated - amount;
        if (change > 0) {
            outputs.add(new Transaction.TxOutput(sender, change));
        }
        return new Transaction(inputs, outputs);
    }

    //   Mine a block from pending transactions:
    //   - Create a coinbase to miner
    //   - Validate and apply txs in order against a temporary UTXO clone
    //   - Build the block, mine it (PoW), append to chain
    //   - Apply the block's transactions to the real UTXO set
    public void minePendingTransactions(String minerAddress) {
        // Create coinbase tx
        Transaction coinbase = Transaction.coinbase(minerAddress, miningReward);

        // Re-validate pending txs against current UTXO state in the order they were received
        List<Transaction> blockTxs = new ArrayList<>();
        blockTxs.add(coinbase);
        
        UTXOSet temp = cloneUtxo();

        // Apply coinbase first
        if (!temp.validateTransaction(coinbase)) {
            throw new IllegalStateException("Invalid coinbase construction");
        }
        temp.applyTransaction(coinbase);

        Iterator<Transaction> it = pendingTransactions.iterator();
        while (it.hasNext()) {
            Transaction tx = it.next();
            if (temp.validateTransaction(tx)) {
                temp.applyTransaction(tx);
                blockTxs.add(tx);
                it.remove();
                utxo.releaseInputs(tx); // will be removed from reserved and then applied below
            } else {
                // keep it in mempool (could be conflicting, insufficient, or now invalid)
            }
        }

        // Create and mine the block
        Block block = new Block(chain.size(), blockTxs, getLatestBlock().hash);
        block.mineBlock(difficulty);
        chain.add(block);

        // Apply block to the real UTXO set
        for (Transaction tx : blockTxs) {
            utxo.applyTransaction(tx);
        }
    }

    //   Pretty-print the chain using Gson.
    public void printChain() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println(gson.toJson(chain));
    }

    //   Save the blockchain (chain only) to a JSON file.
    public void saveBlockchain(String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(chain, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //   Load a blockchain from a JSON file; if not found, return a new one.
    //   After loading, rebuild UTXO from chain and clear pendingTransactions.
    public static Blockchain loadBlockchain(String filename) {
        try (Reader reader = new FileReader(filename)) {
            Gson gson = new Gson();
            List<Block> loadedChain = gson.fromJson(reader, new TypeToken<List<Block>>(){}.getType());
            Blockchain blockchain = new Blockchain();
            if (loadedChain != null && !loadedChain.isEmpty()) {
                blockchain.chain = loadedChain;
                blockchain.pendingTransactions = new ArrayList<>();
                blockchain.utxo.rebuildFromChain(blockchain.chain);
            }
            return blockchain;
        } catch (IOException e) {
            System.out.println("No saved blockchain found, starting new one.");
            return new Blockchain();
        }
    }

    // Utility to check balance by summing UTXOs
    public double checkBalance(String address) {
        double balance = 0.0;
        Map<String, Transaction.TxOutput> snapshot = utxoSnapshot();
        for (Transaction.TxOutput out : snapshot.values()) {
            if (out.receiver.equals(address)) balance += out.amount;
        }
        return balance;
    }

    //   Validate the chain:
    //   - Check prevHash linkage
    //   - Recompute and verify each block's hash
    //   - Verify PoW leading zeros for each block
    public boolean isChainValid() {
        for (int i = 1; i < chain.size(); i++) {
            Block curr = chain.get(i);
            Block prev = chain.get(i - 1);
            if (!curr.previousHash.equals(prev.hash)) return false;
            if (!curr.calculateHash().equals(curr.hash)) return false;
            if (!curr.hash.startsWith("0".repeat(difficulty))) return false;
        }
        return true;
    }

    // Build a snapshot map of UTXOs by replaying the chain.
    private Map<String, Transaction.TxOutput> utxoSnapshot() {
        Map<String, Transaction.TxOutput> map = new HashMap<>();
        UTXOSet temp = new UTXOSet();
        temp.rebuildFromChain(chain);
        for (Block b : chain) {
            for (Transaction tx : b.transactions) {
                // remove spent
                if (!tx.isCoinbase) {
                    for (Transaction.TxInput in : tx.inputs) {
                        map.remove(in.prevTxId + ":" + in.outputIndex);
                    }
                }
                // add outputs
                for (int i = 0; i < tx.outputs.size(); i++) {
                    map.put(tx.transactionId + ":" + i, tx.outputs.get(i));
                }
            }
        }
        return map;
    }

    //   Create a fresh UTXOSet clone by rebuilding from the current chain.
    //   Used for temporary in-block validation during mining.
    private UTXOSet cloneUtxo() {
        UTXOSet clone = new UTXOSet();
        clone.rebuildFromChain(chain);
        return clone;
    }
}
