package com.example.family;

/**
 * TCP'den gelen SET/GET komutlarını parse eder.
 */
public class CommandParser {

    public enum CommandType {
        SET, GET, STATS, UNKNOWN
    }

    public static class Command {
        public final CommandType type;
        public final int id;
        public final String message;

        public Command(CommandType type, int id, String message) {
            this.type = type;
            this.id = id;
            this.message = message;
        }
    }

    /**
     * Komut satırını parse et.
     * Format:
     * SET <id> <message>
     * GET <id>
     */
    public static Command parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return new Command(CommandType.UNKNOWN, -1, null);
        }

        String[] parts = line.trim().split("\\s+", 3);
        String cmd = parts[0].toUpperCase();

        // STATS komutu id gerektirmez
        if (cmd.equals("STATS")) {
            return new Command(CommandType.STATS, -1, null);
        }

        if (parts.length < 2) {
            return new Command(CommandType.UNKNOWN, -1, null);
        }

        int id;
        try {
            id = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return new Command(CommandType.UNKNOWN, -1, null);
        }

        switch (cmd) {
            case "SET":
                String msg = parts.length > 2 ? parts[2] : "";
                return new Command(CommandType.SET, id, msg);
            case "GET":
                return new Command(CommandType.GET, id, null);
            default:
                return new Command(CommandType.UNKNOWN, -1, null);
        }
    }
}
