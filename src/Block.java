//Name: On Tuan Huy
//sID: s4028018

import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

//   Block represents a basic PoW block in a blockchain.
//   - Contains an index, timestamp, list of transactions, prev hash, own hash, and nonce.
//   - Supports hashing and mining by adjusting nonce until hash has leading zeros per difficulty.
public class Block {
    public int index;
    public long timestamp;
    public List<Transaction> transactions;
    public String previousHash;
    public String hash;
    public int nonce;

    //   Construct a new block with given index, transactions, and previous hash.
    //   Calculates initial hash with nonce = 0.
    public Block(int index, List<Transaction> transactions, String previousHash) {
        this.index = index;
        this.timestamp = System.currentTimeMillis();
        this.transactions = new ArrayList<>(transactions);
        this.previousHash = previousHash;
        this.nonce = 0;
        this.hash = calculateHash();
    }

    //   Deterministically compute the block's SHA-256 hash over core fields.
    //   Note: Using transactions.toString() for simplicity; a stable serialization would be preferable for production.
    public String calculateHash() {
        try {
            String data = index
                    + Long.toString(timestamp)
                    + transactions.toString()
                    + previousHash
                    + Integer.toString(nonce);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //   Mine the block by incrementing nonce until the hash has `difficulty` leading zeros.
    //   difficulty = number of leading '0' characters required.
    public void mineBlock(int difficulty) {
        String target = "0".repeat(difficulty);
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash();
        }
        System.out.println("Block mined: " + hash);
    }
}
