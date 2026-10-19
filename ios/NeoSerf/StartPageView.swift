import SwiftUI

struct StartPageView: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var query = ""
    @State private var showAddDial = false
    @FocusState private var searchFocused: Bool
    @State private var dialTitle = ""
    @State private var dialUrl = ""
    @State private var dialCategory = "Быстрый доступ"

    var body: some View {
        GeometryReader { geo in
            ScrollView {
                VStack(spacing: 0) {
                    FinLogo(size: 96)
                    Spacer().frame(height: 14)
                    Text("NeoSerf")
                        .font(NeoFont.headlineLarge())
                        .foregroundColor(palette.text)
                        .multilineTextAlignment(.center)
                    if model.activeTab.isIncognito {
                        Spacer().frame(height: 8)
                        Text("Инкогнито")
                            .font(NeoFont.bodyMedium())
                            .foregroundColor(palette.tertiary)
                    }
                    Spacer().frame(height: 32)
                    searchField
                    if model.preferences.showWeatherWidget {
                        Spacer().frame(height: 28)
                        WeatherWidget(state: model.weather) {
                            Task { await model.refreshWeather() }
                        }
                    }
                    if model.preferences.showSpeedDial {
                        Spacer().frame(height: 32)
                        speedDialSection
                    }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 40)
                .frame(maxWidth: 480)
                .frame(maxWidth: .infinity)
                .frame(minHeight: geo.size.height, alignment: .center)
            }
        }
        .background(palette.background.ignoresSafeArea())
        .accessibilityIdentifier("start_page")
        .overlay { if showAddDial { addDialDialog } }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            NeoIcon(name: NeoIcons.search, size: 21)
                .foregroundColor(palette.secondaryText)
            ZStack(alignment: .leading) {
                if query.isEmpty {
                    Text("Поиск или адрес сайта")
                        .font(NeoFont.bodyLarge())
                        .foregroundColor(palette.secondaryText)
                        .lineLimit(1)
                }
                TextField("", text: $query)
                    .font(NeoFont.bodyLarge())
                    .foregroundColor(palette.text)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .submitLabel(.go)
                    .focused($searchFocused)
                    .onSubmit { submit() }
                    .accessibilityIdentifier("omnibox_input")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 16)
            Button(action: submit) {
                NeoIcon(name: NeoIcons.arrowForward, size: 20)
                    .foregroundColor(palette.primary)
                    .frame(width: 48, height: 48)
            }
            .accessibilityLabel("Найти")
            .accessibilityIdentifier("start_search_submit")
        }
        .padding(.leading, 18)
        .padding(.trailing, 4)
        .frame(minHeight: 56)
        .background(palette.surfaceVariant)
        .overlay(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(searchFocused ? palette.primary : palette.outline, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    private var speedDialSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Быстрый доступ")
                    .font(NeoFont.titleLarge())
                    .foregroundColor(palette.text)
                Spacer()
                Button(action: {
                    dialTitle = ""
                    dialUrl = ""
                    dialCategory = "Быстрый доступ"
                    showAddDial = true
                }) {
                    HStack(spacing: 4) {
                        NeoIcon(name: NeoIcons.add, size: 18)
                        Text("Добавить сайт")
                    }
                    .font(NeoFont.bodyLarge())
                    .foregroundColor(palette.primary)
                }
                .accessibilityIdentifier("add_speed_dial_button")
            }
            if model.speedDial.isEmpty {
                Text("Добавьте любимые сайты на стартовую страницу для мгновенного перехода.")
                    .font(NeoFont.bodyMedium())
                    .foregroundColor(palette.secondaryText)
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(palette.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            } else {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    ForEach(model.speedDial) { tile in
                        SpeedDialCard(tile: tile, palette: palette) {
                            model.submitAddressOrQuery(tile.url)
                        } onDelete: {
                            model.deleteSpeedDial(tile.id)
                        }
                    }
                }
            }
        }
    }

    private var addDialDialog: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { showAddDial = false }
            VStack(alignment: .leading, spacing: 10) {
                Text("Новая карточка быстрого доступа")
                    .font(NeoFont.titleLarge())
                    .foregroundColor(palette.text)
                labeledField("Название сайта", text: $dialTitle, tag: "speed_dial_title_input")
                labeledField("Адрес (URL)", text: $dialUrl, tag: "speed_dial_url_input")
                labeledField("Категория", text: $dialCategory, tag: "speed_dial_category_input")
                HStack {
                    Button("Отмена") { showAddDial = false }.foregroundColor(palette.primary)
                    Spacer()
                    Button("Сохранить") {
                        if !dialUrl.trimmingCharacters(in: .whitespaces).isEmpty {
                            model.addSpeedDial(title: dialTitle, rawUrl: dialUrl, category: dialCategory)
                            showAddDial = false
                        }
                    }
                    .foregroundColor(palette.onPrimary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(palette.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .accessibilityIdentifier("speed_dial_save_button")
                }
            }
            .padding(20)
            .background(palette.surface)
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(palette.outline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(24)
        }
    }

    private func labeledField(_ label: String, text: Binding<String>, tag: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(NeoFont.labelLarge()).foregroundColor(palette.secondaryText)
            TextField(label, text: text)
                .font(NeoFont.bodyLarge())
                .padding(10)
                .background(palette.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .accessibilityIdentifier(tag)
        }
    }

    private func submit() {
        let input = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { return }
        searchFocused = false
        model.submitAddressOrQuery(input)
    }
}

private struct SpeedDialCard: View {
    let tile: SpeedDialTile
    let palette: Palette
    let onOpen: () -> Void
    let onDelete: () -> Void

    var body: some View {
        let initial = String(tile.title.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased()
        let domain = AdBlocker.extractDomain(tile.url)
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                ZStack {
                    Circle().fill(palette.primaryContainer)
                    Text(initial.isEmpty ? "N" : initial)
                        .font(NeoFont.titleMedium().weight(.bold))
                        .foregroundColor(palette.primary)
                }
                .frame(width: 34, height: 34)
                Spacer()
                Button(action: onDelete) {
                    NeoIcon(name: NeoIcons.close, size: 14)
                        .foregroundColor(palette.secondaryText)
                }
                .accessibilityLabel("Удалить")
            }
            Text(tile.title)
                .font(NeoFont.bodyMedium().weight(.semibold))
                .foregroundColor(palette.text)
                .lineLimit(1)
            Text(domain.isEmpty ? tile.url : domain)
                .font(NeoFont.labelSmall())
                .foregroundColor(palette.secondaryText)
                .lineLimit(1)
        }
        .padding(12)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(palette.outline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .onTapGesture(perform: onOpen)
        .accessibilityIdentifier("speed_dial_tile_\(tile.id)")
    }
}

struct WeatherWidget: View {
    let state: WeatherState
    let onRefresh: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            switch state {
            case .loading:
                HStack(spacing: 10) {
                    ProgressView().scaleEffect(0.7)
                    Text("Определяем погоду…")
                        .font(NeoFont.bodyMedium())
                        .foregroundColor(palette.secondaryText)
                }
            case .error(let message):
                Text(message)
                    .font(NeoFont.bodyMedium())
                    .foregroundColor(palette.secondaryText)
                    .multilineTextAlignment(.center)
                Button("Повторить", action: onRefresh)
                    .font(NeoFont.bodyLarge())
                    .foregroundColor(palette.primary)
                    .accessibilityIdentifier("weather_refresh_button")
            case .success(let weather):
                HStack(spacing: 10) {
                    NeoIcon(name: icon(for: weather), size: 22)
                        .foregroundColor(palette.primary)
                    Text("\(Int(weather.temperatureC.rounded()))°")
                        .font(NeoFont.headlineMedium())
                        .foregroundColor(palette.text)
                    Text(weather.cityName)
                        .font(NeoFont.bodyLarge())
                        .foregroundColor(palette.text)
                        .lineLimit(1)
                }
                Text("\(weather.conditionText) · ощущается как \(Int(weather.apparentTempC.rounded()))°")
                    .font(NeoFont.bodyMedium())
                    .foregroundColor(palette.secondaryText)
                    .multilineTextAlignment(.center)
                Text("\(weather.locationSource) · \(weather.dataSource)")
                    .font(NeoFont.bodySmall())
                    .foregroundColor(palette.secondaryText)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityIdentifier("weather_widget_card")
    }

    private func icon(for weather: WeatherForecast) -> String {
        switch weather.weatherCode {
        case 0, 1: return weather.isDay ? NeoIcons.wbSunny : NeoIcons.nightsStay
        case 2, 3: return NeoIcons.cloud
        case 45, 48: return NeoIcons.blurOn
        case 71, 73, 75, 77, 85, 86: return NeoIcons.acUnit
        case 95, 96, 99: return NeoIcons.thunderstorm
        default: return NeoIcons.waterDrop
        }
    }
}
