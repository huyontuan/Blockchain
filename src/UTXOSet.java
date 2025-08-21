//Name: On Tuan Huy
//sID: s4028018

import java.util.*;

//   UTXOSet tracks unspent transaction outputs and supports:
//   - Validation of transactions against current UTXOs and reserved inputs
//   - Reservation of inputs when txs enter mempool to avoid conflicts
//   - Applying transactions (spend inputs, add outputs)
//   - Rebuild from chain by replaying all blocks and transactions

//   Keys are in the form "txId:index" for simplicity.
public class UTXOSet {

    // UTXO map: "txId:index" -> TxOutput
    private final Map<String, Transaction.TxOutput> utxos = new HashMap<>();

    // Reserved inputs by mempool txs to prevent double spends before mining
    private final Set<String> reserved = new HashSet<>();

    // Add UTXOs for a coinbase or after applying a block/tx
    public void addOutput(String txId, int index, Transaction.TxOutput out) {
        utxos.put(txId + ":" + index, out);
    }

    // Remove UTXO when it’s spent
    public void removeOutput(String txId, int index) {
        utxos.remove(txId + ":" + index);
        reserved.remove(txId + ":" + index);
    }

    public boolean exists(String txId, int index) {
        return utxos.containsKey(txId + ":" + index);
    }

    public Transaction.TxOutput get(String txId, int index) {
        return utxos.get(txId + ":" + index);
    }

    // Validate a transaction against the UTXO set and mempool reservations
    public boolean validateTransaction(Transaction tx) {
        if (tx.isCoinbase) {
            // No inputs, only check outputs are sane
            return tx.outputs != null && !tx.outputs.isEmpty() && tx.outputs.stream().allMatch(o -> o.amount > 0);
        }

        // All inputs must exist and not be reserved
        double inputSum = 0.0;
        Set<String> seenInputs = new HashSet<>();
        for (Transaction.TxInput in : tx.inputs) {
            String key = in.prevTxId + ":" + in.outputIndex;

            // No duplicates within this tx
            if (!seenInputs.add(key)) return false;

            // Must exist and not be reserved already (by other pending tx)
            Transaction.TxOutput referenced = utxos.get(key);
            if (referenced == null) return false;
            if (reserved.contains(key)) return false;

            // Minimal ownership/signature verification skipped here
            inputSum += referenced.amount;
        }

        // Outputs must be positive and not exceed inputs
        if (tx.outputs == null || tx.outputs.isEmpty()) return false;
        double outputSum = 0.0;
        for (Transaction.TxOutput out : tx.outputs) {
            if (out.amount <= 0) return false;
            outputSum += out.amount;
        }
        if (outputSum > inputSum) return false;

        return true;
    }

    // Reserve inputs when adding tx to mempool
    public void reserveInputs(Transaction tx) {
        if (tx.isCoinbase) return;
        for (Transaction.TxInput in : tx.inputs) {
            reserved.add(in.prevTxId + ":" + in.outputIndex);
        }
    }

    // Release reservations (e.g., if tx removed from mempool)
    public void releaseInputs(Transaction tx) {
        if (tx.isCoinbase) return;
        for (Transaction.TxInput in : tx.inputs) {
            reserved.remove(in.prevTxId + ":" + in.outputIndex);
        }
    }

    // Apply a validated transaction to the UTXO set
    public void applyTransaction(Transaction tx) {
        if (!tx.isCoinbase) {
            for (Transaction.TxInput in : tx.inputs) {
                removeOutput(in.prevTxId, in.outputIndex);
            }
        }
        for (int i = 0; i < tx.outputs.size(); i++) {
            addOutput(tx.transactionId, i, tx.outputs.get(i));
        }
    }

    // Apply a block atomically (assuming all its txs were validated)
    public void applyBlock(Block block, List<Transaction> txs) {
        for (Transaction tx : txs) {
            applyTransaction(tx);
        }
    }

    // Rebuild from chain when loading
    public void rebuildFromChain(List<Block> chain) {
        utxos.clear();
        reserved.clear();
        for (Block b : chain) {
            // Expect transactions serialized in block; adapt if your Block stores differently
            for (Object obj : b.transactions) {
                Transaction tx = (Transaction) obj;
                // Spend inputs
                if (!tx.isCoinbase) {
                    for (Transaction.TxInput in : tx.inputs) {
                        removeOutput(in.prevTxId, in.outputIndex);
                    }
                }
                // Add outputs
                for (int i = 0; i < tx.outputs.size(); i++) {
                    addOutput(tx.transactionId, i, tx.outputs.get(i));
                }
            }
        }
    }
}
