# converter.py
# Версия на Python с использованием dataclasses, enum, argparse, JSON

import sys
import json
import argparse
import os
from enum import Enum
from dataclasses import dataclass
from typing import Dict, Optional, List, Tuple
import readline  # для истории ввода

# ANSI-цвета
class Colors:
    RESET = '\033[0m'
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    BOLD = '\033[1m'

def colorize(text: str, color: str) -> str:
    return f"{color}{text}{Colors.RESET}"

# Категории и единицы
class Category(Enum):
    LENGTH = "length"
    AREA = "area"
    VOLUME = "volume"
    MASS = "mass"
    PRESSURE = "pressure"
    TEMPERATURE = "temperature"
    ANGLE = "angle"
    SPEED = "speed"

# Базовые единицы (система СИ)
UNITS = {
    Category.LENGTH: {
        "m": 1.0, "cm": 0.01, "mm": 0.001, "km": 1000.0,
        "in": 0.0254, "ft": 0.3048, "yd": 0.9144, "mi": 1609.344
    },
    Category.AREA: {
        "m2": 1.0, "cm2": 0.0001, "ft2": 0.09290304, "yd2": 0.83612736,
        "acre": 4046.8564224, "ha": 10000.0
    },
    Category.VOLUME: {
        "m3": 1.0, "cm3": 1e-6, "l": 0.001, "ft3": 0.028316846592,
        "yd3": 0.764554857984, "gal": 0.003785411784
    },
    Category.MASS: {
        "kg": 1.0, "g": 0.001, "mg": 1e-6, "lb": 0.45359237,
        "oz": 0.028349523125, "t": 1000.0
    },
    Category.PRESSURE: {
        "pa": 1.0, "kpa": 1000.0, "mpa": 1e6, "bar": 100000.0,
        "psi": 6894.757293168, "atm": 101325.0
    },
    Category.TEMPERATURE: {
        # особый случай: не линейная шкала, используем функции
        "c": "celsius", "f": "fahrenheit", "k": "kelvin"
    },
    Category.ANGLE: {
        "deg": 1.0, "rad": 57.29577951308232, "grad": 0.9
    },
    Category.SPEED: {
        "ms": 1.0, "kmh": 0.27777777777778, "mph": 0.44704, "kn": 0.51444444444444
    }
}

# Функции для температуры
def temp_convert(value: float, from_unit: str, to_unit: str) -> float:
    # Сначала переводим в Кельвины
    if from_unit == "c":
        kelvin = value + 273.15
    elif from_unit == "f":
        kelvin = (value + 459.67) * 5/9
    elif from_unit == "k":
        kelvin = value
    else:
        raise ValueError(f"Неизвестная единица температуры: {from_unit}")
    # Из Кельвинов в целевое
    if to_unit == "c":
        return kelvin - 273.15
    elif to_unit == "f":
        return kelvin * 9/5 - 459.67
    elif to_unit == "k":
        return kelvin
    else:
        raise ValueError(f"Неизвестная единица температуры: {to_unit}")

@dataclass
class Conversion:
    value: float
    from_unit: str
    to_unit: str
    result: float
    category: Category

class Converter:
    def __init__(self):
        self.history: List[Conversion] = []
        self.config_file = os.path.expanduser("~/.converter_config.json")
        self.load_config()

    def load_config(self):
        if os.path.exists(self.config_file):
            with open(self.config_file, 'r') as f:
                data = json.load(f)
                self.history = [Conversion(**h) for h in data.get('history', [])]

    def save_config(self):
        with open(self.config_file, 'w') as f:
            json.dump({'history': [h.__dict__ for h in self.history]}, f, indent=2)

    def get_category(self, unit: str) -> Optional[Category]:
        for cat, units in UNITS.items():
            if unit in units:
                return cat
            # для температуры
            if cat == Category.TEMPERATURE and unit in UNITS[Category.TEMPERATURE]:
                return cat
        return None

    def convert(self, value: float, from_unit: str, to_unit: str) -> Tuple[float, Category]:
        from_cat = self.get_category(from_unit)
        to_cat = self.get_category(to_unit)
        if from_cat is None or to_cat is None:
            raise ValueError("Неизвестная единица измерения")
        if from_cat != to_cat:
            raise ValueError("Единицы из разных категорий")
        cat = from_cat

        if cat == Category.TEMPERATURE:
            result = temp_convert(value, from_unit, to_unit)
        else:
            # Линейная конвертация через базовую единицу
            factor_from = UNITS[cat][from_unit]
            factor_to = UNITS[cat][to_unit]
            in_base = value * factor_from
            result = in_base / factor_to

        # Сохраняем историю
        self.history.append(Conversion(value, from_unit, to_unit, result, cat))
        if len(self.history) > 100:
            self.history = self.history[-100:]
        self.save_config()
        return result, cat

    def display_result(self, value: float, from_unit: str, to_unit: str, result: float, cat: Category):
        print(f"\n{colorize(f'{value:.2f}', Colors.CYAN)} {colorize(from_unit, Colors.YELLOW)} "
              f"{colorize('=', Colors.BOLD)} {colorize(f'{result:.2f}', Colors.GREEN)} {colorize(to_unit, Colors.YELLOW)}")
        print(f"Категория: {colorize(cat.value, Colors.BLUE)}\n")

def interactive(converter: Converter):
    print(colorize("Строительный конвертер единиц (интерактивный режим)", Colors.BOLD))
    print("Введите 'q' для выхода, 'history' для истории")
    while True:
        try:
            value_str = input(colorize("Значение: ", Colors.CYAN))
            if value_str.lower() == 'q':
                break
            if value_str.lower() == 'history':
                for h in converter.history[-10:]:
                    print(f"{h.value:.2f} {h.from_unit} = {h.result:.2f} {h.to_unit} ({h.category.value})")
                continue
            value = float(value_str)
            from_unit = input(colorize("Из единицы: ", Colors.CYAN)).strip().lower()
            if from_unit == 'q': break
            to_unit = input(colorize("В единицу: ", Colors.CYAN)).strip().lower()
            if to_unit == 'q': break
            result, cat = converter.convert(value, from_unit, to_unit)
            converter.display_result(value, from_unit, to_unit, result, cat)
        except ValueError as e:
            print(colorize(f"Ошибка: {e}", Colors.RED))
        except KeyboardInterrupt:
            break

def main():
    parser = argparse.ArgumentParser(description='Construction Unit Converter')
    parser.add_argument('value', nargs='?', type=float, help='Значение для конвертации')
    parser.add_argument('from_unit', nargs='?', help='Исходная единица')
    parser.add_argument('to_unit', nargs='?', help='Целевая единица')
    parser.add_argument('-o', '--output', help='Сохранить результат в файл')
    parser.add_argument('--history', action='store_true', help='Показать историю')
    parser.add_argument('--clear-history', action='store_true', help='Очистить историю')
    args = parser.parse_args()

    converter = Converter()

    if args.clear_history:
        converter.history = []
        converter.save_config()
        print("История очищена.")
        return

    if args.history:
        for h in converter.history[-20:]:
            print(f"{h.value:.2f} {h.from_unit} = {h.result:.2f} {h.to_unit} ({h.category.value})")
        return

    if args.value is not None and args.from_unit and args.to_unit:
        try:
            result, cat = converter.convert(args.value, args.from_unit, args.to_unit)
            converter.display_result(args.value, args.from_unit, args.to_unit, result, cat)
            if args.output:
                with open(args.output, 'a') as f:
                    f.write(f"{args.value} {args.from_unit} = {result} {args.to_unit}\n")
                print(colorize(f"Результат сохранён в {args.output}", Colors.GREEN))
        except Exception as e:
            print(colorize(f"Ошибка: {e}", Colors.RED))
            sys.exit(1)
    else:
        interactive(converter)

if __name__ == '__main__':
    main()
