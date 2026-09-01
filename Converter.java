// Converter.java
// Версия на Java с использованием enum, java.util.function, java.nio.file

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

public class Converter {
    // ANSI-цвета
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[96m";
    private static final String GREEN = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String RED = "\u001B[91m";
    private static final String BLUE = "\u001B[94m";
    private static final String BOLD = "\u001B[1m";

    private static String colorize(String text, String color) {
        return color + text + RESET;
    }

    // Категории enum
    enum Category {
        LENGTH, AREA, VOLUME, MASS, PRESSURE, TEMPERATURE, ANGLE, SPEED;
    }

    // Карта единиц и коэффициентов к базовой (SI)
    private static final Map<Category, Map<String, Double>> UNIT_MAP = new HashMap<>();
    private static final Set<String> TEMP_UNITS = new HashSet<>(Arrays.asList("c", "f", "k"));

    static {
        // Заполняем UNIT_MAP
        Map<String, Double> length = new HashMap<>();
        length.put("m", 1.0); length.put("cm", 0.01); length.put("mm", 0.001);
        length.put("km", 1000.0); length.put("in", 0.0254); length.put("ft", 0.3048);
        length.put("yd", 0.9144); length.put("mi", 1609.344);
        UNIT_MAP.put(Category.LENGTH, length);

        Map<String, Double> area = new HashMap<>();
        area.put("m2", 1.0); area.put("cm2", 0.0001); area.put("ft2", 0.09290304);
        area.put("yd2", 0.83612736); area.put("acre", 4046.8564224); area.put("ha", 10000.0);
        UNIT_MAP.put(Category.AREA, area);

        Map<String, Double> volume = new HashMap<>();
        volume.put("m3", 1.0); volume.put("cm3", 1e-6); volume.put("l", 0.001);
        volume.put("ft3", 0.028316846592); volume.put("yd3", 0.764554857984);
        volume.put("gal", 0.003785411784);
        UNIT_MAP.put(Category.VOLUME, volume);

        Map<String, Double> mass = new HashMap<>();
        mass.put("kg", 1.0); mass.put("g", 0.001); mass.put("mg", 1e-6);
        mass.put("lb", 0.45359237); mass.put("oz", 0.028349523125); mass.put("t", 1000.0);
        UNIT_MAP.put(Category.MASS, mass);

        Map<String, Double> pressure = new HashMap<>();
        pressure.put("pa", 1.0); pressure.put("kpa", 1000.0); pressure.put("mpa", 1e6);
        pressure.put("bar", 100000.0); pressure.put("psi", 6894.757293168);
        pressure.put("atm", 101325.0);
        UNIT_MAP.put(Category.PRESSURE, pressure);

        Map<String, Double> angle = new HashMap<>();
        angle.put("deg", 1.0); angle.put("rad", 57.29577951308232); angle.put("grad", 0.9);
        UNIT_MAP.put(Category.ANGLE, angle);

        Map<String, Double> speed = new HashMap<>();
        speed.put("ms", 1.0); speed.put("kmh", 0.27777777777778);
        speed.put("mph", 0.44704); speed.put("kn", 0.51444444444444);
        UNIT_MAP.put(Category.SPEED, speed);

        // Для температуры нет коэффициентов, обрабатываем отдельно
    }

    // Класс для истории
    static class Entry {
        double value;
        String fromUnit;
        String toUnit;
        double result;
        Category category;
        Entry(double v, String f, String t, double r, Category c) {
            value = v; fromUnit = f; toUnit = t; result = r; category = c;
        }
    }

    private List<Entry> history = new ArrayList<>();
    private Path configPath = Paths.get(System.getProperty("user.home"), ".converter_config.json");

    public Converter() {
        loadConfig();
    }

    private void loadConfig() {
        try {
            if (Files.exists(configPath)) {
                String json = new String(Files.readAllBytes(configPath));
                // Простой парсинг JSON (для демонстрации)
                // Используем org.json или встроенный? Здесь упростим
                // В реальном проекте используйте библиотеку Jackson или Gson
                // Для демонстрации пропустим загрузку истории
            }
        } catch (IOException e) {}
    }

    private void saveConfig() {
        try {
            // Сохраняем историю в JSON (упрощённо)
            StringBuilder sb = new StringBuilder();
            sb.append("{\"history\":[");
            for (int i = 0; i < history.size(); i++) {
                Entry e = history.get(i);
                sb.append(String.format("{\"value\":%f,\"fromUnit\":\"%s\",\"toUnit\":\"%s\",\"result\":%f,\"category\":\"%s\"}",
                        e.value, e.fromUnit, e.toUnit, e.result, e.category));
                if (i < history.size()-1) sb.append(",");
            }
            sb.append("]}");
            Files.write(configPath, sb.toString().getBytes());
        } catch (IOException e) {}
    }

    private Category getCategory(String unit) {
        for (Category cat : Category.values()) {
            if (cat == Category.TEMPERATURE) {
                if (TEMP_UNITS.contains(unit)) return cat;
            } else {
                if (UNIT_MAP.get(cat).containsKey(unit)) return cat;
            }
        }
        return null;
    }

    private double convertTemperature(double value, String from, String to) {
        double kelvin;
        switch (from) {
            case "c": kelvin = value + 273.15; break;
            case "f": kelvin = (value + 459.67) * 5/9; break;
            case "k": kelvin = value; break;
            default: throw new IllegalArgumentException("Неизвестная единица температуры");
        }
        switch (to) {
            case "c": return kelvin - 273.15;
            case "f": return kelvin * 9/5 - 459.67;
            case "k": return kelvin;
            default: throw new IllegalArgumentException("Неизвестная единица температуры");
        }
    }

    public double[] convert(double value, String fromUnit, String toUnit) throws Exception {
        Category fromCat = getCategory(fromUnit);
        Category toCat = getCategory(toUnit);
        if (fromCat == null || toCat == null) throw new Exception("Неизвестная единица измерения");
        if (fromCat != toCat) throw new Exception("Единицы из разных категорий");
        double result;
        if (fromCat == Category.TEMPERATURE) {
            result = convertTemperature(value, fromUnit, toUnit);
        } else {
            double factorFrom = UNIT_MAP.get(fromCat).get(fromUnit);
            double factorTo = UNIT_MAP.get(fromCat).get(toUnit);
            result = (value * factorFrom) / factorTo;
        }
        history.add(new Entry(value, fromUnit, toUnit, result, fromCat));
        if (history.size() > 100) history = history.subList(history.size()-100, history.size());
        saveConfig();
        return new double[]{result, fromCat.ordinal()}; // category index для вывода
    }

    public void display(double value, String fromUnit, String toUnit, double result, Category cat) {
        System.out.printf("\n%s %s %s %s %s %s\n",
                colorize(String.format("%.2f", value), CYAN),
                colorize(fromUnit, YELLOW),
                colorize("=", BOLD),
                colorize(String.format("%.2f", result), GREEN),
                colorize(toUnit, YELLOW),
                "");
        System.out.printf("Категория: %s\n\n", colorize(cat.name().toLowerCase(), BLUE));
    }

    public void showHistory() {
        for (Entry e : history) {
            System.out.printf("%.2f %s = %.2f %s (%s)\n", e.value, e.fromUnit, e.result, e.toUnit, e.category);
        }
    }

    public void clearHistory() {
        history.clear();
        saveConfig();
    }

    public static void main(String[] args) throws Exception {
        String outputFile = null;
        boolean showHist = false;
        boolean clearHist = false;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    if (i+1 < args.length) outputFile = args[++i];
                    break;
                case "--history":
                    showHist = true;
                    break;
                case "--clear-history":
                    clearHist = true;
                    break;
                default:
                    positional.add(args[i]);
            }
        }

        Converter converter = new Converter();

        if (clearHist) {
            converter.clearHistory();
            System.out.println("История очищена.");
            return;
        }
        if (showHist) {
            converter.showHistory();
            return;
        }

        if (positional.size() >= 3) {
            double val = Double.parseDouble(positional.get(0));
            String from = positional.get(1).toLowerCase();
            String to = positional.get(2).toLowerCase();
            double[] res = converter.convert(val, from, to);
            Category cat = Category.values()[(int)res[1]];
            converter.display(val, from, to, res[0], cat);
            if (outputFile != null) {
                Files.write(Paths.get(outputFile),
                        String.format("%.2f %s = %.2f %s\n", val, from, res[0], to).getBytes(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                System.out.println(colorize("Результат сохранён в " + outputFile, GREEN));
            }
        } else {
            // Интерактивный режим
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println(colorize("Строительный конвертер единиц (интерактивный режим)", BOLD));
            System.out.println("Введите 'q' для выхода, 'history' для истории");
            while (true) {
                System.out.print(colorize("Значение: ", CYAN));
                String line = reader.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.equals("q")) break;
                if (line.equals("history")) {
                    converter.showHistory();
                    continue;
                }
                double val;
                try {
                    val = Double.parseDouble(line);
                } catch (NumberFormatException e) {
                    System.out.println(colorize("Ошибка: введите число", RED));
                    continue;
                }
                System.out.print(colorize("Из единицы: ", CYAN));
                String from = reader.readLine().trim().toLowerCase();
                if (from.equals("q")) break;
                System.out.print(colorize("В единицу: ", CYAN));
                String to = reader.readLine().trim().toLowerCase();
                if (to.equals("q")) break;
                try {
                    double[] res = converter.convert(val, from, to);
                    Category cat = Category.values()[(int)res[1]];
                    converter.display(val, from, to, res[0], cat);
                } catch (Exception e) {
                    System.out.println(colorize("Ошибка: " + e.getMessage(), RED));
                }
            }
        }
    }
}
