// Converter.cs
// Версия на C# с использованием enum, Dictionary, Func, JSON

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace UnitConverter
{
    // ANSI-цвета
    public static class Colors
    {
        public const string Reset = "\u001b[0m";
        public const string Cyan = "\u001b[96m";
        public const string Green = "\u001b[92m";
        public const string Yellow = "\u001b[93m";
        public const string Red = "\u001b[91m";
        public const string Blue = "\u001b[94m";
        public const string Bold = "\u001b[1m";

        public static string Colorize(string text, string color) => color + text + Reset;
    }

    public enum Category
    {
        Length, Area, Volume, Mass, Pressure, Temperature, Angle, Speed
    }

    public class ConversionEntry
    {
        public double Value { get; set; }
        public string FromUnit { get; set; }
        public string ToUnit { get; set; }
        public double Result { get; set; }
        public string Category { get; set; }
    }

    public class Converter
    {
        private static readonly Dictionary<Category, Dictionary<string, double>> UnitMap = new();
        private static readonly HashSet<string> TempUnits = new() { "c", "f", "k" };

        static Converter()
        {
            UnitMap[Category.Length] = new Dictionary<string, double>
            {
                {"m", 1}, {"cm", 0.01}, {"mm", 0.001}, {"km", 1000},
                {"in", 0.0254}, {"ft", 0.3048}, {"yd", 0.9144}, {"mi", 1609.344}
            };
            UnitMap[Category.Area] = new Dictionary<string, double>
            {
                {"m2", 1}, {"cm2", 0.0001}, {"ft2", 0.09290304},
                {"yd2", 0.83612736}, {"acre", 4046.8564224}, {"ha", 10000}
            };
            UnitMap[Category.Volume] = new Dictionary<string, double>
            {
                {"m3", 1}, {"cm3", 1e-6}, {"l", 0.001},
                {"ft3", 0.028316846592}, {"yd3", 0.764554857984}, {"gal", 0.003785411784}
            };
            UnitMap[Category.Mass] = new Dictionary<string, double>
            {
                {"kg", 1}, {"g", 0.001}, {"mg", 1e-6},
                {"lb", 0.45359237}, {"oz", 0.028349523125}, {"t", 1000}
            };
            UnitMap[Category.Pressure] = new Dictionary<string, double>
            {
                {"pa", 1}, {"kpa", 1000}, {"mpa", 1e6},
                {"bar", 100000}, {"psi", 6894.757293168}, {"atm", 101325}
            };
            UnitMap[Category.Angle] = new Dictionary<string, double>
            {
                {"deg", 1}, {"rad", 57.29577951308232}, {"grad", 0.9}
            };
            UnitMap[Category.Speed] = new Dictionary<string, double>
            {
                {"ms", 1}, {"kmh", 0.27777777777778},
                {"mph", 0.44704}, {"kn", 0.51444444444444}
            };
        }

        private readonly List<ConversionEntry> history = new();
        private readonly string configFile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".converter_config.json");

        public Converter()
        {
            LoadConfig();
        }

        private void LoadConfig()
        {
            if (File.Exists(configFile))
            {
                try
                {
                    string json = File.ReadAllText(configFile);
                    var data = JsonSerializer.Deserialize<Dictionary<string, List<ConversionEntry>>>(json);
                    if (data != null && data.ContainsKey("history"))
                        history.AddRange(data["history"]);
                }
                catch { }
            }
        }

        private void SaveConfig()
        {
            var data = new Dictionary<string, List<ConversionEntry>> { { "history", history } };
            string json = JsonSerializer.Serialize(data, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(configFile, json);
        }

        private Category? GetCategory(string unit)
        {
            unit = unit.ToLower();
            foreach (var kv in UnitMap)
            {
                if (kv.Value.ContainsKey(unit)) return kv.Key;
            }
            if (TempUnits.Contains(unit)) return Category.Temperature;
            return null;
        }

        private double ConvertTemperature(double value, string from, string to)
        {
            double kelvin;
            switch (from.ToLower())
            {
                case "c": kelvin = value + 273.15; break;
                case "f": kelvin = (value + 459.67) * 5 / 9; break;
                case "k": kelvin = value; break;
                default: throw new Exception("Неизвестная единица температуры");
            }
            switch (to.ToLower())
            {
                case "c": return kelvin - 273.15;
                case "f": return kelvin * 9 / 5 - 459.67;
                case "k": return kelvin;
                default: throw new Exception("Неизвестная единица температуры");
            }
        }

        public (double result, Category category) Convert(double value, string fromUnit, string toUnit)
        {
            var fromCat = GetCategory(fromUnit);
            var toCat = GetCategory(toUnit);
            if (fromCat == null || toCat == null) throw new Exception("Неизвестная единица измерения");
            if (fromCat != toCat) throw new Exception("Единицы из разных категорий");
            double result;
            if (fromCat == Category.Temperature)
            {
                result = ConvertTemperature(value, fromUnit, toUnit);
            }
            else
            {
                double factorFrom = UnitMap[fromCat.Value][fromUnit.ToLower()];
                double factorTo = UnitMap[fromCat.Value][toUnit.ToLower()];
                result = (value * factorFrom) / factorTo;
            }
            history.Add(new ConversionEntry { Value = value, FromUnit = fromUnit, ToUnit = toUnit, Result = result, Category = fromCat.Value.ToString().ToLower() });
            if (history.Count > 100) history.RemoveRange(0, history.Count - 100);
            SaveConfig();
            return (result, fromCat.Value);
        }

        public void Display(double value, string fromUnit, string toUnit, double result, Category category)
        {
            Console.WriteLine($"\n{Colors.Colorize($"{value:F2}", Colors.Cyan)} {Colors.Colorize(fromUnit, Colors.Yellow)} {Colors.Colorize("=", Colors.Bold)} {Colors.Colorize($"{result:F2}", Colors.Green)} {Colors.Colorize(toUnit, Colors.Yellow)}");
            Console.WriteLine($"Категория: {Colors.Colorize(category.ToString().ToLower(), Colors.Blue)}\n");
        }

        public void ShowHistory()
        {
            foreach (var e in history.TakeLast(20))
            {
                Console.WriteLine($"{e.Value:F2} {e.FromUnit} = {e.Result:F2} {e.ToUnit} ({e.Category})");
            }
        }

        public void ClearHistory()
        {
            history.Clear();
            SaveConfig();
        }
    }

    class Program
    {
        static void Main(string[] args)
        {
            var converter = new Converter();
            string outputFile = null;
            bool showHistory = false;
            bool clearHistory = false;
            var positional = new List<string>();

            for (int i = 0; i < args.Length; i++)
            {
                switch (args[i])
                {
                    case "-o":
                        if (i + 1 < args.Length) outputFile = args[++i];
                        break;
                    case "--history":
                        showHistory = true;
                        break;
                    case "--clear-history":
                        clearHistory = true;
                        break;
                    default:
                        positional.Add(args[i]);
                        break;
                }
            }

            if (clearHistory)
            {
                converter.ClearHistory();
                Console.WriteLine("История очищена.");
                return;
            }
            if (showHistory)
            {
                converter.ShowHistory();
                return;
            }

            if (positional.Count >= 3)
            {
                try
                {
                    double val = double.Parse(positional[0]);
                    string from = positional[1].ToLower();
                    string to = positional[2].ToLower();
                    var (result, category) = converter.Convert(val, from, to);
                    converter.Display(val, from, to, result, category);
                    if (!string.IsNullOrEmpty(outputFile))
                    {
                        File.AppendAllText(outputFile, $"{val:F2} {from} = {result:F2} {to}\n");
                        Console.WriteLine(Colors.Colorize($"Результат сохранён в {outputFile}", Colors.Green));
                    }
                }
                catch (Exception e)
                {
                    Console.WriteLine(Colors.Colorize($"Ошибка: {e.Message}", Colors.Red));
                }
            }
            else
            {
                // Интерактивный режим
                Console.WriteLine(Colors.Colorize("Строительный конвертер единиц (интерактивный режим)", Colors.Bold));
                Console.WriteLine("Введите 'q' для выхода, 'history' для истории");
                while (true)
                {
                    Console.Write(Colors.Colorize("Значение: ", Colors.Cyan));
                    string line = Console.ReadLine();
                    if (line == null) break;
                    line = line.Trim();
                    if (line == "q") break;
                    if (line == "history")
                    {
                        converter.ShowHistory();
                        continue;
                    }
                    if (!double.TryParse(line, out double val))
                    {
                        Console.WriteLine(Colors.Colorize("Ошибка: введите число", Colors.Red));
                        continue;
                    }
                    Console.Write(Colors.Colorize("Из единицы: ", Colors.Cyan));
                    string from = Console.ReadLine()?.Trim().ToLower();
                    if (from == "q") break;
                    Console.Write(Colors.Colorize("В единицу: ", Colors.Cyan));
                    string to = Console.ReadLine()?.Trim().ToLower();
                    if (to == "q") break;
                    try
                    {
                        var (result, category) = converter.Convert(val, from, to);
                        converter.Display(val, from, to, result, category);
                    }
                    catch (Exception e)
                    {
                        Console.WriteLine(Colors.Colorize($"Ошибка: {e.Message}", Colors.Red));
                    }
                }
            }
        }
    }
}
