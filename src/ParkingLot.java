import java.util.*;
import java.time.*;
import java.time.format.*;

/**
 * Problem 8: Parking Lot Management with Open Addressing
 * Demonstrates: Array-based hash table, linear probing, custom hash function,
 *               EMPTY/OCCUPIED/DELETED slot states, load factor management
 */
public class ParkingLot {

    // ── Slot States ───────────────────────────────────────────────────────────

    private enum SlotState { EMPTY, OCCUPIED, DELETED }

    // ── Slot ─────────────────────────────────────────────────────────────────

    private static class Slot {
        SlotState state = SlotState.EMPTY;
        String licensePlate;
        LocalDateTime entryTime;

        void occupy(String plate) {
            this.state = SlotState.OCCUPIED;
            this.licensePlate = plate;
            this.entryTime = LocalDateTime.now();
        }

        void vacate() {
            this.state = SlotState.DELETED; // tombstone → allows probing to continue
            this.licensePlate = null;
            this.entryTime = null;
        }

        double parkingFee(LocalDateTime exitTime) {
            long minutes = Duration.between(entryTime, exitTime).toMinutes();
            double hours = Math.ceil(minutes / 60.0);
            return Math.max(1, hours) * 5.50; // $5.50/hour, min $5.50
        }
    }

    // ── Hash Table (Open Addressing) ──────────────────────────────────────────

    private final Slot[] table;
    private final int capacity;
    private int occupiedCount = 0;

    // Stats
    private long totalProbes = 0;
    private long totalParkings = 0;
    private final Map<Integer, Integer> hourlyParkings = new TreeMap<>();

    public ParkingLot(int capacity) {
        this.capacity = capacity;
        this.table = new Slot[capacity];
        for (int i = 0; i < capacity; i++) table[i] = new Slot();
    }

    // ── Hash Function ─────────────────────────────────────────────────────────

    /**
     * Custom hash for license plates.
     * Uses polynomial rolling hash: sum(char[i] * 31^i) % capacity
     */
    private int hash(String licensePlate) {
        long h = 0, prime = 31, mod = capacity;
        for (char c : licensePlate.toCharArray()) {
            h = (h * prime + c) % mod;
        }
        return (int) ((h + mod) % mod); // always positive
    }

    // ── Core Operations ────────────────────────────────────────────────────────

    /**
     * Park a vehicle using linear probing.
     * Returns the assigned slot number and probe count.
     */
    public ParkResult parkVehicle(String licensePlate) {
        if (occupiedCount >= capacity * 0.9) {
            return new ParkResult(-1, 0, "Parking lot is full (>90% capacity)");
        }

        int preferred = hash(licensePlate);
        int probes = 0;
        int firstDeleted = -1; // reuse tombstone slots

        for (int i = 0; i < capacity; i++) {
            int idx = (preferred + i) % capacity;
            Slot slot = table[idx];
            probes++;

            if (slot.state == SlotState.DELETED && firstDeleted == -1) {
                firstDeleted = idx; // remember first tombstone
            }

            if (slot.state == SlotState.EMPTY) {
                // Use tombstone slot if found earlier (closer to preferred)
                int target = (firstDeleted != -1) ? firstDeleted : idx;
                table[target].occupy(licensePlate);
                occupiedCount++;
                totalProbes += probes;
                totalParkings++;
                recordHour();
                String msg = probes == 1
                        ? String.format("Assigned spot #%d (%d probe)", target, probes - 1)
                        : String.format("Assigned spot #%d (%d probes, preferred was #%d)",
                                target, probes - 1, preferred);
                return new ParkResult(target, probes - 1, msg);
            }

            if (slot.state == SlotState.OCCUPIED && licensePlate.equals(slot.licensePlate)) {
                return new ParkResult(idx, probes - 1, "Vehicle already parked at slot #" + idx);
            }
        }

        return new ParkResult(-1, probes, "No available slot found");
    }

    /**
     * Vehicle exits. Marks slot as DELETED (tombstone) to preserve probe chains.
     */
    public ExitResult exitVehicle(String licensePlate) {
        int preferred = hash(licensePlate);
        for (int i = 0; i < capacity; i++) {
            int idx = (preferred + i) % capacity;
            Slot slot = table[idx];
            if (slot.state == SlotState.EMPTY) break; // never parked here
            if (slot.state == SlotState.OCCUPIED && licensePlate.equals(slot.licensePlate)) {
                LocalDateTime exit = LocalDateTime.now();
                double fee = slot.parkingFee(exit);
                LocalDateTime entry = slot.entryTime;
                slot.vacate();
                occupiedCount--;
                return new ExitResult(idx, entry, exit, fee,
                        String.format("Spot #%d freed, Fee: $%.2f", idx, fee));
            }
        }
        return new ExitResult(-1, null, null, 0, "Vehicle not found: " + licensePlate);
    }

    /** Find nearest available spot to entrance (slot 0). */
    public int findNearestAvailable() {
        for (int i = 0; i < capacity; i++) {
            if (table[i].state != SlotState.OCCUPIED) return i;
        }
        return -1;
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public void getStatistics() {
        double occupancy = 100.0 * occupiedCount / capacity;
        double avgProbes = totalParkings == 0 ? 0 : (double) totalProbes / totalParkings;

        int peakHour = hourlyParkings.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(-1);

        System.out.printf("%ngetStatistics() →%n");
        System.out.printf("  Occupancy    : %.0f%% (%d/%d spots)%n", occupancy, occupiedCount, capacity);
        System.out.printf("  Avg Probes   : %.1f%n", avgProbes);
        System.out.printf("  Peak Hour    : %d:00 - %d:00%n", peakHour, peakHour + 1);
        System.out.printf("  Load Factor  : %.2f (rehash threshold: 0.90)%n", (double) occupiedCount / capacity);
        System.out.printf("  Total parked : %,d vehicles%n", totalParkings);
    }

    private void recordHour() {
        int hour = LocalDateTime.now().getHour();
        hourlyParkings.merge(hour, 1, Integer::sum);
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record ParkResult(int spotNumber, int probes, String message) {}
    public record ExitResult(int spotNumber, LocalDateTime entry, LocalDateTime exit,
                             double fee, String message) {}

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        ParkingLot lot = new ParkingLot(500);

        System.out.println("=== Parking Lot Management (Open Addressing) ===\n");

        ParkResult r1 = lot.parkVehicle("ABC-1234");
        System.out.println("parkVehicle(\"ABC-1234\") → " + r1.message());

        ParkResult r2 = lot.parkVehicle("ABC-1235");
        System.out.println("parkVehicle(\"ABC-1235\") → " + r2.message());

        ParkResult r3 = lot.parkVehicle("XYZ-9999");
        System.out.println("parkVehicle(\"XYZ-9999\") → " + r3.message());

        // Small pause to accumulate some "time"
        Thread.sleep(50);

        ExitResult e1 = lot.exitVehicle("ABC-1234");
        System.out.println("\nexitVehicle(\"ABC-1234\") → " + e1.message());

        // Park 400 more vehicles to demonstrate load factor effects
        System.out.println("\nParking 400 more vehicles...");
        Random rng = new Random(1);
        for (int i = 0; i < 400; i++) {
            String plate = String.format("%c%c%c-%04d",
                    (char)('A' + rng.nextInt(26)),
                    (char)('A' + rng.nextInt(26)),
                    (char)('A' + rng.nextInt(26)),
                    rng.nextInt(9999));
            lot.parkVehicle(plate);
        }

        System.out.println("Nearest available spot: #" + lot.findNearestAvailable());
        lot.getStatistics();

        System.out.println("\n--- Open Addressing vs Chaining ---");
        System.out.println("Open Addressing: better cache locality, no pointer overhead");
        System.out.println("Chaining       : simpler deletion, handles high load factors better");
    }
}
