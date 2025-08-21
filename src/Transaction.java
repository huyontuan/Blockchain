//Name: On Tuan Huy
//sID: s4028018

import java.util.*;

//   Transaction with:
//   - Inputs: references to previous UTXOs (by txId and output index) and a placeholder signature
//   - Outputs: payments to receivers with amounts
//   - Coinbase flag: special transaction with no inputs that mints new coins
//   - Deterministic transactionId derived from inputs/outputs
public class Transaction {
    public static class TxInput {
        public String prevTxId;
        public int outputIndex;
        public String signature; // placeholder; not verifying cryptographically in this minimal version

        public TxInput(String prevTxId, int outputIndex, String signature) {
            this.prevTxId = prevTxId;
            this.outputIndex = outputIndex;
            this.signature = signature;
        }

        @Override public String toString() {
            return "in{" + prevTxId + ":" + outputIndex + "}";
        }
    }

    //   Output paying an amount to a receiver (address string).
    public static class TxOutput {
        public String receiver;
        public double amount;

        public TxOutput(String receiver, double amount) {
            this.receiver = receiver;
            this.amount = amount;
        }

        @Override public String toString() {
            return "out{" + receiver + ":" + amount + "}";
        }
    }

    // Deterministic identifier for the transaction (hash of content)
    public String transactionId;

    // List of inputs and outputs
    public List<TxInput> inputs = new ArrayList<>();
    public List<TxOutput> outputs = new ArrayList<>();

    // True if this is a coinbase transaction
    public boolean isCoinbase;

    //   Create a coinbase transaction that mints new coins to receiver.
    //   No inputs; one output for the minted amount.
    public static Transaction coinbase(String receiver, double amount) {
        Transaction tx = new Transaction(true);
        tx.outputs.add(new TxOutput(receiver, amount));
        tx.finalizeId();
        return tx;
    }

    //   Regular transaction with specified inputs and outputs.
    public Transaction(List<TxInput> inputs, List<TxOutput> outputs) {
        this.isCoinbase = false;
        this.inputs.addAll(inputs);
        this.outputs.addAll(outputs);
        finalizeId();
    }

    private Transaction(boolean coinbase) {
        this.isCoinbase = coinbase;
    }

    //   Compute a deterministic transactionId by hashing a simple serialization
    //   of inputs and outputs. For a production system, include versions, locktime,
    //   scripts, and use canonical encoding.
    private void finalizeId() {
        StringBuilder sb = new StringBuilder();
        sb.append(isCoinbase ? "coinbase|" : "tx|");
        for (TxInput in : inputs) sb.append(in.prevTxId).append(":").append(in.outputIndex).append("|");
        for (TxOutput out : outputs) sb.append(out.receiver).append(":").append(out.amount).append("|");
        this.transactionId = HashUtil.sha256(sb.toString());
    }

    @Override
    public String toString() {
        return (isCoinbase ? "COINBASE " : "") + transactionId;
    }
}
