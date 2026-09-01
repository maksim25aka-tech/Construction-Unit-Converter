// converter.go
// Версия на Go с использованием структур, карт, горутин для сохранения

package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// ANSI-цвета
const (
	reset  = "\033[0m"
	cyan   = "\033[96m"
	green  = "\033[92m"
	yellow = "\033[93m"
	red    = "\033[91m"
	blue   = "\033[94m"
	bold   = "\033[1m"
)

func colorize(text, color string) string {
	return color + text + reset
}

// Категории и единицы
var categories = map[string]map[string]float64{
	"length": {
		"m": 1, "cm": 0.01, "mm": 0.001, "km": 1000,
		"in": 0.0254, "ft": 0.3048, "yd": 0.9144, "mi": 1609.344,
	},
	"area": {
		"m2": 1, "cm2": 0.0001, "ft2": 0.09290304, "yd2": 0.83612736,
		"acre": 4046.8564224, "ha": 10000,
	},
	"volume": {
		"m3": 1, "cm3": 1e-6, "l": 0.001, "ft3": 0.028316846592,
		"yd3": 0.764554857984, "gal": 0.003785411784,
	},
	"mass": {
		"kg": 1, "g": 0.001, "mg": 1e-6, "lb": 0.45359237,
		"oz": 0.028349523125, "t": 1000,
	},
	"pressure": {
		"pa": 1, "kpa": 1000, "mpa": 1e6, "bar": 100000,
		"psi": 6894.757293168, "atm": 101325,
	},
	"temperature": {
		// температура обрабатывается отдельно, но для единиц используем строки
	},
	"angle": {
		"deg": 1, "rad": 57.29577951308232, "grad": 0.9,
	},
	"speed": {
		"ms": 1, "kmh": 0.27777777777778, "mph": 0.44704, "kn": 0.51444444444444,
	},
}

var tempUnits = []string{"c", "f", "k"}

// Структура для истории
type ConversionEntry struct {
	Value    float64 `json:"value"`
	FromUnit string  `json:"fromUnit"`
	ToUnit   string  `json:"toUnit"`
	Result   float64 `json:"result"`
	Category string  `json:"category"`
}

type Converter struct {
	history []ConversionEntry
}

func NewConverter() *Converter {
	c := &Converter{history: []ConversionEntry{}}
	c.loadConfig()
	return c
}

func (c *Converter) configPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".converter_config.json")
}

func (c *Converter) loadConfig() {
	data, err := os.ReadFile(c.configPath())
	if err != nil {
		return
	}
	var config struct {
		History []ConversionEntry `json:"history"`
	}
	if err := json.Unmarshal(data, &config); err == nil {
		c.history = config.History
	}
}

func (c *Converter) saveConfig() {
	config := struct {
		History []ConversionEntry `json:"history"`
	}{History: c.history}
	data, _ := json.MarshalIndent(config, "", "  ")
	os.WriteFile(c.configPath(), data, 0644)
}

func (c *Converter) getCategory(unit string) string {
	for cat, units := range categories {
		if _, ok := units[unit]; ok {
			return cat
		}
	}
	// проверка температуры
	for _, u := range tempUnits {
		if unit == u {
			return "temperature"
		}
	}
	return ""
}

func (c *Converter) convertTemperature(value float64, from, to string) float64 {
	var kelvin float64
	switch from {
	case "c":
		kelvin = value + 273.15
	case "f":
		kelvin = (value + 459.67) * 5 / 9
	case "k":
		kelvin = value
	default:
		panic("Неизвестная единица температуры")
	}
	switch to {
	case "c":
		return kelvin - 273.15
	case "f":
		return kelvin*9/5 - 459.67
	case "k":
		return kelvin
	default:
		panic("Неизвестная единица температуры")
	}
}

func (c *Converter) Convert(value float64, fromUnit, toUnit string) (float64, string, error) {
	fromCat := c.getCategory(fromUnit)
	toCat := c.getCategory(toUnit)
	if fromCat == "" || toCat == "" {
		return 0, "", fmt.Errorf("неизвестная единица измерения")
	}
	if fromCat != toCat {
		return 0, "", fmt.Errorf("единицы из разных категорий")
	}
	var result float64
	if fromCat == "temperature" {
		result = c.convertTemperature(value, fromUnit, toUnit)
	} else {
		factorFrom := categories[fromCat][fromUnit]
		factorTo := categories[fromCat][toUnit]
		result = (value * factorFrom) / factorTo
	}
	// Сохраняем историю
	entry := ConversionEntry{Value: value, FromUnit: fromUnit, ToUnit: toUnit, Result: result, Category: fromCat}
	c.history = append(c.history, entry)
	if len(c.history) > 100 {
		c.history = c.history[len(c.history)-100:]
	}
	go c.saveConfig() // асинхронное сохранение
	return result, fromCat, nil
}

func (c *Converter) Display(value float64, fromUnit, toUnit string, result float64, category string) {
	fmt.Printf("\n%s %s %s %s %s %s\n",
		colorize(fmt.Sprintf("%.2f", value), cyan),
		colorize(fromUnit, yellow),
		colorize("=", bold),
		colorize(fmt.Sprintf("%.2f", result), green),
		colorize(toUnit, yellow),
	)
	fmt.Printf("Категория: %s\n\n", colorize(category, blue))
}

func (c *Converter) ShowHistory() {
	for _, h := range c.history {
		fmt.Printf("%.2f %s = %.2f %s (%s)\n", h.Value, h.FromUnit, h.Result, h.ToUnit, h.Category)
	}
}

func (c *Converter) ClearHistory() {
	c.history = []ConversionEntry{}
	c.saveConfig()
}

func main() {
	var outputFile string
	var showHistory bool
	var clearHistory bool

	flag.StringVar(&outputFile, "o", "", "Сохранить результат в файл")
	flag.BoolVar(&showHistory, "history", false, "Показать историю")
	flag.BoolVar(&clearHistory, "clear-history", false, "Очистить историю")
	flag.Usage = func() {
		fmt.Println("Использование: go run converter.go [значение] [из_единица] [в_единица] [опции]")
		fmt.Println("  -o, --output <file>   Сохранить результат в файл")
		fmt.Println("  --history             Показать историю")
		fmt.Println("  --clear-history       Очистить историю")
	}
	flag.Parse()

	converter := NewConverter()

	if clearHistory {
		converter.ClearHistory()
		fmt.Println("История очищена.")
		return
	}
	if showHistory {
		converter.ShowHistory()
		return
	}

	args := flag.Args()
	if len(args) >= 3 {
		val, err := strconv.ParseFloat(args[0], 64)
		if err != nil {
			fmt.Println(colorize("Ошибка: неверное значение", red))
			os.Exit(1)
		}
		fromUnit := strings.ToLower(args[1])
		toUnit := strings.ToLower(args[2])
		result, category, err := converter.Convert(val, fromUnit, toUnit)
		if err != nil {
			fmt.Println(colorize("Ошибка: "+err.Error(), red))
			os.Exit(1)
		}
		converter.Display(val, fromUnit, toUnit, result, category)
		if outputFile != "" {
			f, _ := os.OpenFile(outputFile, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
			defer f.Close()
			fmt.Fprintf(f, "%.2f %s = %.2f %s\n", val, fromUnit, result, toUnit)
			fmt.Println(colorize("Результат сохранён в "+outputFile, green))
		}
	} else {
		// Интерактивный режим
		scanner := bufio.NewScanner(os.Stdin)
		fmt.Println(colorize("Строительный конвертер единиц (интерактивный режим)", bold))
		fmt.Println("Введите 'q' для выхода, 'history' для истории")
		for {
			fmt.Print(colorize("Значение: ", cyan))
			if !scanner.Scan() {
				break
			}
			line := strings.TrimSpace(scanner.Text())
			if line == "q" {
				break
			}
			if line == "history" {
				for _, h := range converter.history {
					fmt.Printf("%.2f %s = %.2f %s (%s)\n", h.Value, h.FromUnit, h.Result, h.ToUnit, h.Category)
				}
				continue
			}
			val, err := strconv.ParseFloat(line, 64)
			if err != nil {
				fmt.Println(colorize("Ошибка: введите число", red))
				continue
			}
			fmt.Print(colorize("Из единицы: ", cyan))
			if !scanner.Scan() {
				break
			}
			fromUnit := strings.ToLower(strings.TrimSpace(scanner.Text()))
			if fromUnit == "q" {
				break
			}
			fmt.Print(colorize("В единицу: ", cyan))
			if !scanner.Scan() {
				break
			}
			toUnit := strings.ToLower(strings.TrimSpace(scanner.Text()))
			if toUnit == "q" {
				break
			}
			result, category, err := converter.Convert(val, fromUnit, toUnit)
			if err != nil {
				fmt.Println(colorize("Ошибка: "+err.Error(), red))
				continue
			}
			converter.Display(val, fromUnit, toUnit, result, category)
		}
	}
}
