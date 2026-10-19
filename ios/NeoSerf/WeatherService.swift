import CoreLocation
import Foundation

enum WeatherService {
    static func mapCode(_ code: Int) -> String {
        switch code {
        case 0: return "Ясно"
        case 1: return "Преимущественно ясно"
        case 2: return "Переменная облачность"
        case 3: return "Пасмурно"
        case 45, 48: return "Туман"
        case 51, 53, 55: return "Морось"
        case 56, 57: return "Ледяная морось"
        case 61, 63, 65: return "Дождь"
        case 66, 67: return "Ледяной дождь"
        case 71, 73, 75, 77: return "Снег"
        case 80, 81, 82: return "Ливень"
        case 85, 86: return "Снежный ливень"
        case 95, 96, 99: return "Гроза"
        default: return "Нет описания"
        }
    }

    static func fetch(latitude: Double? = nil, longitude: Double? = nil, cityHint: String? = nil) async throws -> WeatherForecast {
        var lat = latitude
        var lon = longitude
        var city = cityHint ?? "Ваш регион"
        var country = ""
        var source = cityHint == nil ? "По сети" : "По местоположению"

        if lat == nil || lon == nil {
            let geo = try await requestJSON("https://ipwho.is/?lang=ru&fields=success,latitude,longitude,city,region,country")
            guard geo["success"] as? Bool == true else { throw WeatherError.region }
            lat = geo["latitude"] as? Double
            lon = geo["longitude"] as? Double
            city = (geo["city"] as? String).flatMap { $0.isEmpty ? nil : $0 }
                ?? (geo["region"] as? String).flatMap { $0.isEmpty ? nil : $0 }
                ?? "Ваш регион"
            country = geo["country"] as? String ?? ""
            source = "По сети"
        }
        guard let latitude = lat, let longitude = lon,
              latitude >= -90, latitude <= 90, longitude >= -180, longitude <= 180 else {
            throw WeatherError.region
        }

        do {
            let url = "https://api.open-meteo.com/v1/forecast?latitude=\(latitude)&longitude=\(longitude)&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
            let json = try await requestJSON(url)
            return try parseOpenMeteo(json, city: city, country: country, source: source)
        } catch {
            let url = String(format: "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", latitude, longitude)
            let json = try await requestJSON(url)
            return try parseMetNo(json, city: city, country: country, source: source)
        }
    }

    private static func parseOpenMeteo(_ json: [String: Any], city: String, country: String, source: String) throws -> WeatherForecast {
        guard let current = json["current"] as? [String: Any],
              let daily = json["daily"] as? [String: Any] else { throw WeatherError.parse }
        let code = intValue(current["weather_code"])
        let temperature = doubleValue(current["temperature_2m"])
        let apparent = doubleValue(current["apparent_temperature"])
        let wind = doubleValue(current["wind_speed_10m"])
        let humidity = intValue(current["relative_humidity_2m"])
        let high = (daily["temperature_2m_max"] as? [Any]).flatMap { doubleValue($0.first) } ?? temperature
        let low = (daily["temperature_2m_min"] as? [Any]).flatMap { doubleValue($0.first) } ?? temperature
        return WeatherForecast(
            cityName: city, country: country, temperatureC: temperature, apparentTempC: apparent,
            windSpeedKmh: wind, humidityPercent: humidity, weatherCode: code,
            isDay: intValue(current["is_day"]) == 1, locationSource: source, dataSource: "Open-Meteo",
            conditionText: mapCode(code), dailyMaxC: high, dailyMinC: low
        )
    }

    private static func parseMetNo(_ json: [String: Any], city: String, country: String, source: String) throws -> WeatherForecast {
        guard let properties = json["properties"] as? [String: Any],
              let series = properties["timeseries"] as? [[String: Any]],
              let first = series.first,
              let data = first["data"] as? [String: Any],
              let instant = data["instant"] as? [String: Any],
              let details = instant["details"] as? [String: Any] else { throw WeatherError.parse }
        let temperature = doubleValue(details["air_temperature"])
        let humidity = Int(doubleValue(details["relative_humidity"]).rounded())
        let wind = doubleValue(details["wind_speed"]) * 3.6
        func symbol(_ period: String) -> String {
            (((data[period] as? [String: Any])?["summary"] as? [String: Any])?["symbol_code"] as? String) ?? ""
        }
        let symbolCode = symbol("next_1_hours").isEmpty ? symbol("next_6_hours") : symbol("next_1_hours")
        let kind = symbolCode.split(separator: "_").first.map(String.init) ?? ""
        let code: Int
        if kind.contains("thunder") { code = 95 }
        else if kind.contains("snow") { code = 73 }
        else if kind.contains("sleet") || kind.contains("rain") { code = kind.hasPrefix("light") ? 61 : 63 }
        else if kind == "fog" { code = 45 }
        else if kind == "clearsky" { code = 0 }
        else if kind == "fair" { code = 1 }
        else if kind == "partlycloudy" { code = 2 }
        else if kind == "cloudy" { code = 3 }
        else { code = -1 }
        let dayTemps: [Double] = series.prefix(24).compactMap { item in
            let details = ((item["data"] as? [String: Any])?["instant"] as? [String: Any])?["details"] as? [String: Any]
            return details.map { doubleValue($0["air_temperature"]) }
        }
        return WeatherForecast(
            cityName: city, country: country, temperatureC: temperature, apparentTempC: temperature,
            windSpeedKmh: wind, humidityPercent: humidity, weatherCode: code,
            isDay: !symbolCode.hasSuffix("_night"), locationSource: source, dataSource: "MET Norway",
            conditionText: mapCode(code), dailyMaxC: dayTemps.max() ?? temperature, dailyMinC: dayTemps.min() ?? temperature
        )
    }

    private static func requestJSON(_ urlString: String) async throws -> [String: Any] {
        guard let url = URL(string: urlString) else { throw WeatherError.network }
        var request = URLRequest(url: url, timeoutInterval: 12)
        request.setValue("NeoSerf/1.3.4", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw WeatherError.network
        }
        return obj
    }

    private static func doubleValue(_ any: Any?) -> Double {
        if let d = any as? Double { return d }
        if let n = any as? NSNumber { return n.doubleValue }
        if let i = any as? Int { return Double(i) }
        return 0
    }

    private static func intValue(_ any: Any?) -> Int {
        if let i = any as? Int { return i }
        if let n = any as? NSNumber { return n.intValue }
        if let d = any as? Double { return Int(d) }
        return 0
    }

    enum WeatherError: Error { case region, parse, network }
}

final class LocationHelper: NSObject, CLLocationManagerDelegate, @unchecked Sendable {
    static let shared = LocationHelper()
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation?, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    func request() async -> CLLocation? {
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, *) {
            status = manager.authorizationStatus
        } else {
            status = CLLocationManager.authorizationStatus()
        }
        if status == .denied || status == .restricted {
            return manager.location
        }
        return await withCheckedContinuation { continuation in
            self.continuation = continuation
            if status == .notDetermined {
                manager.requestWhenInUseAuthorization()
            } else {
                manager.requestLocation()
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 8) { [weak self] in
                if let pending = self?.continuation {
                    self?.continuation = nil
                    pending.resume(returning: self?.manager.location)
                }
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        continuation?.resume(returning: locations.last)
        continuation = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        continuation?.resume(returning: manager.location)
        continuation = nil
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, *) {
            status = manager.authorizationStatus
        } else {
            status = CLLocationManager.authorizationStatus()
        }
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            manager.requestLocation()
        } else if status == .denied || status == .restricted {
            continuation?.resume(returning: manager.location)
            continuation = nil
        }
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            manager.requestLocation()
        } else if status == .denied || status == .restricted {
            continuation?.resume(returning: manager.location)
            continuation = nil
        }
    }
}
