# converter.rb
# Версия на Ruby с метапрограммированием, OptionParser, цветным выводом

require 'optparse'
require 'json'
require 'fileutils'

# ANSI-цвета
module Color
  RESET = "\033[0m"
  CYAN = "\033[96m"
  GREEN = "\033[92m"
  YELLOW = "\033[93m"
  RED = "\033[91m"
  BLUE = "\033[94m"
  BOLD = "\033[1m"

  def self.colorize(text, color)
    "#{color}#{text}#{RESET}"
  end
end

# Категории и единицы
CATEGORIES = {
  length: {
    'm' => 1, 'cm' => 0.01, 'mm' => 0.001, 'km' => 1000,
    'in' => 0.0254, 'ft' => 0.3048, 'yd' => 0.9144, 'mi' => 1609.344
  },
  area: {
    'm2' => 1, 'cm2' => 0.0001, 'ft2' => 0.09290304,
    'yd2' => 0.83612736, 'acre' => 4046.8564224, 'ha' => 10000
  },
  volume: {
    'm3' => 1, 'cm3' => 1e-6, 'l' => 0.001,
    'ft3' => 0.028316846592, 'yd3' => 0.764554857984, 'gal' => 0.003785411784
  },
  mass: {
    'kg' => 1, 'g' => 0.001, 'mg' => 1e-6,
    'lb' => 0.45359237, 'oz' => 0.028349523125, 't' => 1000
  },
  pressure: {
    'pa' => 1, 'kpa' => 1000, 'mpa' => 1e6,
    'bar' => 100000, 'psi' => 6894.757293168, 'atm' => 101325
  },
  angle: {
    'deg' => 1, 'rad' => 57.29577951308232, 'grad' => 0.9
  },
  speed: {
    'ms' => 1, 'kmh' => 0.27777777777778,
    'mph' => 0.44704, 'kn' => 0.51444444444444
  }
}
TEMP_UNITS = %w[c f k]

class Converter
  attr_reader :history

  def initialize
    @history = []
    @config_file = File.join(Dir.home, '.converter_config.json')
    load_config
  end

  def load_config
    return unless File.exist?(@config_file)
    data = JSON.parse(File.read(@config_file))
    @history = data['history'] if data['history']
  rescue
    @history = []
  end

  def save_config
    File.write(@config_file, JSON.pretty_generate(history: @history))
  end

  def get_category(unit)
    CATEGORIES.each do |cat, units|
      return cat.to_s if units.key?(unit)
    end
    return 'temperature' if TEMP_UNITS.include?(unit)
    nil
  end

  def convert_temperature(value, from, to)
    kelvin = case from
             when 'c' then value + 273.15
             when 'f' then (value + 459.67) * 5/9
             when 'k' then value
             else raise "Неизвестная единица температуры"
             end
    case to
    when 'c' then kelvin - 273.15
    when 'f' then kelvin * 9/5 - 459.67
    when 'k' then kelvin
    else raise "Неизвестная единица температуры"
    end
  end

  def convert(value, from_unit, to_unit)
    from_cat = get_category(from_unit)
    to_cat = get_category(to_unit)
    raise "Неизвестная единица измерения" unless from_cat && to_cat
    raise "Единицы из разных категорий" unless from_cat == to_cat

    result = if from_cat == 'temperature'
               convert_temperature(value, from_unit, to_unit)
             else
               factor_from = CATEGORIES[from_cat.to_sym][from_unit]
               factor_to = CATEGORIES[from_cat.to_sym][to_unit]
               (value * factor_from) / factor_to
             end

    @history << { 'value' => value, 'fromUnit' => from_unit, 'toUnit' => to_unit,
                  'result' => result, 'category' => from_cat }
    @history = @history.last(100) if @history.size > 100
    save_config
    [result, from_cat]
  end

  def display(value, from_unit, to_unit, result, category)
    puts "\n#{Color.colorize(format('%.2f', value), Color::CYAN)} #{Color.colorize(from_unit, Color::YELLOW)} #{Color.colorize('=', Color::BOLD)} #{Color.colorize(format('%.2f', result), Color::GREEN)} #{Color.colorize(to_unit, Color::YELLOW)}"
    puts "Категория: #{Color.colorize(category, Color::BLUE)}\n"
  end

  def show_history
    @history.last(20).each do |h|
      puts format('%.2f %s = %.2f %s (%s)', h['value'], h['fromUnit'], h['result'], h['toUnit'], h['category'])
    end
  end

  def clear_history
    @history = []
    save_config
  end
end

# Парсинг командной строки
options = {}
OptionParser.new do |opts|
  opts.banner = "Использование: ruby converter.rb [значение] [из_единица] [в_единица] [опции]"
  opts.on('-o', '--output FILE', 'Сохранить результат в файл') { |v| options[:output] = v }
  opts.on('--history', 'Показать историю') { options[:history] = true }
  opts.on('--clear-history', 'Очистить историю') { options[:clear_history] = true }
end.parse!

converter = Converter.new

if options[:clear_history]
  converter.clear_history
  puts "История очищена."
  exit
end

if options[:history]
  converter.show_history
  exit
end

if ARGV.size >= 3
  value = ARGV[0].to_f
  from_unit = ARGV[1].downcase
  to_unit = ARGV[2].downcase
  begin
    result, category = converter.convert(value, from_unit, to_unit)
    converter.display(value, from_unit, to_unit, result, category)
    if options[:output]
      File.open(options[:output], 'a') { |f| f.puts "#{format('%.2f', value)} #{from_unit} = #{format('%.2f', result)} #{to_unit}" }
      puts Color.colorize("Результат сохранён в #{options[:output]}", Color::GREEN)
    end
  rescue => e
    puts Color.colorize("Ошибка: #{e.message}", Color::RED)
  end
else
  # Интерактивный режим
  puts Color.colorize("Строительный конвертер единиц (интерактивный режим)", Color::BOLD)
  puts "Введите 'q' для выхода, 'history' для истории"
  loop do
    print Color.colorize("Значение: ", Color::CYAN)
    line = gets.chomp
    break if line == 'q'
    if line == 'history'
      converter.show_history
      next
    end
    unless line =~ /^-?\d+(\.\d+)?$/
      puts Color.colorize("Ошибка: введите число", Color::RED)
      next
    end
    value = line.to_f
    print Color.colorize("Из единицы: ", Color::CYAN)
    from_unit = gets.chomp.downcase
    break if from_unit == 'q'
    print Color.colorize("В единицу: ", Color::CYAN)
    to_unit = gets.chomp.downcase
    break if to_unit == 'q'
    begin
      result, category = converter.convert(value, from_unit, to_unit)
      converter.display(value, from_unit, to_unit, result, category)
    rescue => e
      puts Color.colorize("Ошибка: #{e.message}", Color::RED)
    end
  end
end
