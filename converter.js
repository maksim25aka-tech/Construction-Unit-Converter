//
// Версия на JavaScript с использованием commander, chalk, fs

#!/usr/bin/env node

const { Command } = require('commander');
const chalk = require('chalk');
const fs = require('fs');
const path = require('path');
const os = require('os');

// Конфигурация
const CONFIG_FILE = path.join(os.homedir(), '.converter_config.json');

// Категории и единицы
const CATEGORIES = {
    length: { m: 1, cm: 0.01, mm: 0.001, km: 1000, in: 0.0254, ft: 0.3048, yd: 0.9144, mi: 1609.344 },
    area: { m2: 1, cm2: 0.0001, ft2: 0.09290304, yd2: 0.83612736, acre: 4046.8564224, ha: 10000 },
    volume: { m3: 1, cm3: 1e-6, l: 0.001, ft3: 0.028316846592, yd3: 0.764554857984, gal: 0.003785411784 },
    mass: { kg: 1, g: 0.001, mg: 1e-6, lb: 0.45359237, oz: 0.028349523125, t: 1000 },
    pressure: { pa: 1, kpa: 1000, mpa: 1e6, bar: 100000, psi: 6894.757293168, atm: 101325 },
    temperature: { c: 'celsius', f: 'fahrenheit', k: 'kelvin' },
    angle: { deg: 1, rad: 57.29577951308232, grad: 0.9 },
    speed: { ms: 1, kmh: 0.27777777777778, mph: 0.44704, kn: 0.51444444444444 }
};

// Функции температуры
function tempConvert(value, fromUnit, toUnit) {
    let kelvin;
    if (fromUnit === 'c') kelvin = value + 273.15;
    else if (fromUnit === 'f') kelvin = (value + 459.67) * 5/9;
    else if (fromUnit === 'k') kelvin = value;
    else throw new Error(`Неизвестная единица температуры: ${fromUnit}`);
    if (toUnit === 'c') return kelvin - 273.15;
    if (toUnit === 'f') return kelvin * 9/5 - 459.67;
    if (toUnit === 'k') return kelvin;
    throw new Error(`Неизвестная единица температуры: ${toUnit}`);
}

class Converter {
    constructor() {
        this.history = [];
        this.loadConfig();
    }

    loadConfig() {
        try {
            if (fs.existsSync(CONFIG_FILE)) {
                const data = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
                this.history = data.history || [];
            }
        } catch (e) {}
    }

    saveConfig() {
        fs.writeFileSync(CONFIG_FILE, JSON.stringify({ history: this.history }, null, 2));
    }

    getCategory(unit) {
        for (const [cat, units] of Object.entries(CATEGORIES)) {
            if (units[unit] !== undefined) return cat;
            if (cat === 'temperature' && ['c','f','k'].includes(unit)) return cat;
        }
        return null;
    }

    convert(value, fromUnit, toUnit) {
        const fromCat = this.getCategory(fromUnit);
        const toCat = this.getCategory(toUnit);
        if (!fromCat || !toCat) throw new Error('Неизвестная единица измерения');
        if (fromCat !== toCat) throw new Error('Единицы из разных категорий');
        let result;
        if (fromCat === 'temperature') {
            result = tempConvert(value, fromUnit, toUnit);
        } else {
            const factorFrom = CATEGORIES[fromCat][fromUnit];
            const factorTo = CATEGORIES[fromCat][toUnit];
            result = (value * factorFrom) / factorTo;
        }
        this.history.push({ value, fromUnit, toUnit, result, category: fromCat });
        if (this.history.length > 100) this.history = this.history.slice(-100);
        this.saveConfig();
        return { result, category: fromCat };
    }

    display(value, fromUnit, toUnit, result, category) {
        console.log(`\n${chalk.cyan(value.toFixed(2))} ${chalk.yellow(fromUnit)} ${chalk.bold('=')} ${chalk.green(result.toFixed(2))} ${chalk.yellow(toUnit)}`);
        console.log(`Категория: ${chalk.blue(category)}`);
        console.log();
    }
}

const program = new Command();
program
    .name('converter')
    .description('Construction Unit Converter (JavaScript)')
    .argument('[value]', 'Значение для конвертации')
    .argument('[from]', 'Исходная единица')
    .argument('[to]', 'Целевая единица')
    .option('-o, --output <file>', 'Сохранить результат в файл')
    .option('--history', 'Показать историю')
    .option('--clear-history', 'Очистить историю')
    .action(async (value, from, to, options) => {
        const converter = new Converter();
        if (options.clearHistory) {
            converter.history = [];
            converter.saveConfig();
            console.log('История очищена.');
            return;
        }
        if (options.history) {
            for (const h of converter.history.slice(-20)) {
                console.log(`${h.value.toFixed(2)} ${h.fromUnit} = ${h.result.toFixed(2)} ${h.toUnit} (${h.category})`);
            }
            return;
        }
        if (value !== undefined && from && to) {
            try {
                const val = parseFloat(value);
                const { result, category } = converter.convert(val, from, to);
                converter.display(val, from, to, result, category);
                if (options.output) {
                    fs.appendFileSync(options.output, `${val} ${from} = ${result} ${to}\n`);
                    console.log(chalk.green(`Результат сохранён в ${options.output}`));
                }
            } catch (e) {
                console.error(chalk.red(`Ошибка: ${e.message}`));
                process.exit(1);
            }
        } else {
            // Интерактивный режим
            const rl = require('readline').createInterface({
                input: process.stdin,
                output: process.stdout
            });
            console.log(chalk.bold('Строительный конвертер единиц (интерактивный режим)'));
            console.log('Введите "q" для выхода, "history" для истории');
            const question = (q) => new Promise(resolve => rl.question(chalk.cyan(q), resolve));
            while (true) {
                try {
                    let val = await question('Значение: ');
                    if (val.toLowerCase() === 'q') break;
                    if (val.toLowerCase() === 'history') {
                        for (const h of converter.history.slice(-10)) {
                            console.log(`${h.value.toFixed(2)} ${h.fromUnit} = ${h.result.toFixed(2)} ${h.toUnit} (${h.category})`);
                        }
                        continue;
                    }
                    const valueNum = parseFloat(val);
                    if (isNaN(valueNum)) throw new Error('Введите число');
                    const fromUnit = (await question('Из единицы: ')).trim().toLowerCase();
                    if (fromUnit === 'q') break;
                    const toUnit = (await question('В единицу: ')).trim().toLowerCase();
                    if (toUnit === 'q') break;
                    const { result, category } = converter.convert(valueNum, fromUnit, toUnit);
                    converter.display(valueNum, fromUnit, toUnit, result, category);
                } catch (e) {
                    console.log(chalk.red(`Ошибка: ${e.message}`));
                }
            }
            rl.close();
        }
    });

program.parse(process.argv);
