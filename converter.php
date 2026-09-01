<?php
// converter.php
// Версия на PHP с использованием ассоциативных массивов, анонимных функций

// ANSI-цвета
define('RESET', "\033[0m");
define('CYAN', "\033[96m");
define('GREEN', "\033[92m");
define('YELLOW', "\033[93m");
define('RED', "\033[91m");
define('BLUE', "\033[94m");
define('BOLD', "\033[1m");

function colorize($text, $color) {
    return $color . $text . RESET;
}

// Категории и единицы
$categories = [
    'length' => [
        'm' => 1, 'cm' => 0.01, 'mm' => 0.001, 'km' => 1000,
        'in' => 0.0254, 'ft' => 0.3048, 'yd' => 0.9144, 'mi' => 1609.344
    ],
    'area' => [
        'm2' => 1, 'cm2' => 0.0001, 'ft2' => 0.09290304,
        'yd2' => 0.83612736, 'acre' => 4046.8564224, 'ha' => 10000
    ],
    'volume' => [
        'm3' => 1, 'cm3' => 1e-6, 'l' => 0.001,
        'ft3' => 0.028316846592, 'yd3' => 0.764554857984, 'gal' => 0.003785411784
    ],
    'mass' => [
        'kg' => 1, 'g' => 0.001, 'mg' => 1e-6,
        'lb' => 0.45359237, 'oz' => 0.028349523125, 't' => 1000
    ],
    'pressure' => [
        'pa' => 1, 'kpa' => 1000, 'mpa' => 1e6,
        'bar' => 100000, 'psi' => 6894.757293168, 'atm' => 101325
    ],
    'angle' => [
        'deg' => 1, 'rad' => 57.29577951308232, 'grad' => 0.9
    ],
    'speed' => [
        'ms' => 1, 'kmh' => 0.27777777777778,
        'mph' => 0.44704, 'kn' => 0.51444444444444
    ]
];
$temp_units = ['c', 'f', 'k'];

class Converter {
    private $history = [];
    private $configFile;

    public function __construct() {
        $home = getenv('HOME') ?: getenv('USERPROFILE');
        $this->configFile = $home . '/.converter_config.json';
        $this->loadConfig();
    }

    private function loadConfig() {
        if (file_exists($this->configFile)) {
            $data = json_decode(file_get_contents($this->configFile), true);
            if (isset($data['history'])) $this->history = $data['history'];
        }
    }

    private function saveConfig() {
        file_put_contents($this->configFile, json_encode(['history' => $this->history], JSON_PRETTY_PRINT));
    }

    private function getCategory($unit) {
        global $categories, $temp_units;
        foreach ($categories as $cat => $units) {
            if (array_key_exists($unit, $units)) return $cat;
        }
        if (in_array($unit, $temp_units)) return 'temperature';
        return null;
    }

    private function convertTemperature($value, $from, $to) {
        switch ($from) {
            case 'c': $kelvin = $value + 273.15; break;
            case 'f': $kelvin = ($value + 459.67) * 5/9; break;
            case 'k': $kelvin = $value; break;
            default: throw new Exception("Неизвестная единица температуры");
        }
        switch ($to) {
            case 'c': return $kelvin - 273.15;
            case 'f': return $kelvin * 9/5 - 459.67;
            case 'k': return $kelvin;
            default: throw new Exception("Неизвестная единица температуры");
        }
    }

    public function convert($value, $fromUnit, $toUnit) {
        $fromCat = $this->getCategory($fromUnit);
        $toCat = $this->getCategory($toUnit);
        if (!$fromCat || !$toCat) throw new Exception("Неизвестная единица измерения");
        if ($fromCat != $toCat) throw new Exception("Единицы из разных категорий");
        if ($fromCat == 'temperature') {
            $result = $this->convertTemperature($value, $fromUnit, $toUnit);
        } else {
            global $categories;
            $factorFrom = $categories[$fromCat][$fromUnit];
            $factorTo = $categories[$fromCat][$toUnit];
            $result = ($value * $factorFrom) / $factorTo;
        }
        $this->history[] = ['value' => $value, 'fromUnit' => $fromUnit, 'toUnit' => $toUnit, 'result' => $result, 'category' => $fromCat];
        if (count($this->history) > 100) array_splice($this->history, 0, count($this->history) - 100);
        $this->saveConfig();
        return [$result, $fromCat];
    }

    public function display($value, $fromUnit, $toUnit, $result, $category) {
        echo "\n" . colorize(sprintf("%.2f", $value), CYAN) . " " . colorize($fromUnit, YELLOW) . " " . colorize("=", BOLD) . " " . colorize(sprintf("%.2f", $result), GREEN) . " " . colorize($toUnit, YELLOW) . "\n";
        echo "Категория: " . colorize($category, BLUE) . "\n\n";
    }

    public function showHistory() {
        foreach (array_slice($this->history, -20) as $h) {
            printf("%.2f %s = %.2f %s (%s)\n", $h['value'], $h['fromUnit'], $h['result'], $h['toUnit'], $h['category']);
        }
    }

    public function clearHistory() {
        $this->history = [];
        $this->saveConfig();
    }
}

// Парсинг аргументов
$shortOpts = "o::";
$longOpts = ['history', 'clear-history'];
$options = getopt($shortOpts, $longOpts);
$outputFile = $options['o'] ?? null;
$showHistory = isset($options['history']);
$clearHistory = isset($options['clear-history']);

$positional = array_values(array_filter($argv, function($arg) { return !str_starts_with($arg, '-'); }));
array_shift($positional); // удаляем имя скрипта

$converter = new Converter();

if ($clearHistory) {
    $converter->clearHistory();
    echo "История очищена.\n";
    exit;
}
if ($showHistory) {
    $converter->showHistory();
    exit;
}

if (count($positional) >= 3) {
    $value = (float)$positional[0];
    $from = strtolower($positional[1]);
    $to = strtolower($positional[2]);
    try {
        list($result, $category) = $converter->convert($value, $from, $to);
        $converter->display($value, $from, $to, $result, $category);
        if ($outputFile) {
            file_put_contents($outputFile, sprintf("%.2f %s = %.2f %s\n", $value, $from, $result, $to), FILE_APPEND);
            echo colorize("Результат сохранён в $outputFile", GREEN) . "\n";
        }
    } catch (Exception $e) {
        echo colorize("Ошибка: " . $e->getMessage(), RED) . "\n";
    }
} else {
    // Интерактивный режим
    echo colorize("Строительный конвертер единиц (интерактивный режим)", BOLD) . "\n";
    echo "Введите 'q' для выхода, 'history' для истории\n";
    while (true) {
        echo colorize("Значение: ", CYAN);
        $line = trim(fgets(STDIN));
        if ($line === 'q') break;
        if ($line === 'history') {
            $converter->showHistory();
            continue;
        }
        if (!is_numeric($line)) {
            echo colorize("Ошибка: введите число", RED) . "\n";
            continue;
        }
        $value = (float)$line;
        echo colorize("Из единицы: ", CYAN);
        $from = strtolower(trim(fgets(STDIN)));
        if ($from === 'q') break;
        echo colorize("В единицу: ", CYAN);
        $to = strtolower(trim(fgets(STDIN)));
        if ($to === 'q') break;
        try {
            list($result, $category) = $converter->convert($value, $from, $to);
            $converter->display($value, $from, $to, $result, $category);
        } catch (Exception $e) {
            echo colorize("Ошибка: " . $e->getMessage(), RED) . "\n";
        }
    }
}
