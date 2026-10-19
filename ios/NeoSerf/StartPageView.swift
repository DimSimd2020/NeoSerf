import SwiftUI

struct StartPageView: View {
    @ObservedObject var model: BrowserViewModel
    @Environment(\.palette) private var palette
    @State private var query = ""
    @State private var focused = false
    @State private var showAddDial = false
    @FocusState private var searchFocused: Bool

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Spacer().frame(height: 48)
                Image("Logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 96, height: 96)
                    .accessibilityHidden(true)
                Text("NeoSerf")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(palette.text)
                    .padding(.top, 14)
                if model.activeTab.isIncognito {
                    Text("Инкогнито")
                        .font(.system(size: 15))
                        .foregroundColor(palette.tertiary)
                        .padding(.top, 8)
                }
                searchField
                    .padding(.top, 32)
                if model.preferences.showWeatherWidget {
                    WeatherWidget(state: model.weather) {
                        Task { await model.refreshWeather() }
                    }
                    .padding(.top, 28)
                }
                if model.preferences.showSpeedDial {
                    speedDialSection
                        .padding(.top, 32)
                }
                Spacer().frame(height: 40)
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .background(palette.background.ignoresSafeArea())
        .sheet(isPresented: $showAddDial) {
            AddSpeedDialSheet { title, url, category in
                model.addSpeedDial(title: title, rawUrl: url, category: category)
                showAddDial = false
            } onCancel: {
                showAddDial = false
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 18))
                .foregroundColor(palette.secondaryText)
            ZStack(alignment: .leading) {
                if query.isEmpty {
                    Text("Поиск или адрес сайта")
                        .font(.system(size: 16))
                        .foregroundColor(palette.secondaryText)
                        .lineLimit(1)
                }
                TextField("", text: $query)
                    .font(.system(size: 16))
                    .foregroundColor(palette.text)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .submitLabel(.go)
                    .focused($searchFocused)
                    .onSubmit { submit() }
            }
            Button(action: submit) {
                Image(systemName: "arrow.right")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(palette.primary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Найти")
        }
        .padding(.leading, 18)
        .padding(.trailing, 4)
        .frame(minHeight: 56)
        .background(palette.surfaceVariant)
        .overlay(
            RoundedRectangle(cornerRadius: 28)
                .stroke(searchFocused ? palette.primary : palette.outline, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 28))
    }

    private var speedDialSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Быстрый доступ")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(palette.text)
                Spacer()
                Button(action: { showAddDial = true }) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                        Text("Добавить сайт")
                    }
                    .font(.system(size: 15))
                    .foregroundColor(palette.primary)
                }
            }
            if model.speedDial.isEmpty {
                Text("Добавьте любимые сайты на стартовую страницу для мгновенного перехода.")
                    .font(.system(size: 14))
                    .foregroundColor(palette.secondaryText)
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(palette.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
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
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(palette.primary)
                }
                .frame(width: 34, height: 34)
                Spacer()
                Button(action: onDelete) {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(palette.secondaryText)
                }
            }
            Text(tile.title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(palette.text)
                .lineLimit(1)
            Text(domain.isEmpty ? tile.url : domain)
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(palette.secondaryText)
                .lineLimit(1)
        }
        .padding(12)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(palette.outline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .onTapGesture(perform: onOpen)
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
                    ProgressView().scaleEffect(0.8)
                    Text("Определяем погоду…")
                        .font(.system(size: 14))
                        .foregroundColor(palette.secondaryText)
                }
            case .error(let message):
                Text(message)
                    .font(.system(size: 14))
                    .foregroundColor(palette.secondaryText)
                    .multilineTextAlignment(.center)
                Button("Повторить", action: onRefresh)
                    .foregroundColor(palette.primary)
            case .success(let weather):
                HStack(spacing: 10) {
                    Image(systemName: icon(for: weather))
                        .font(.system(size: 20))
                        .foregroundColor(palette.primary)
                    Text("\(Int(weather.temperatureC.rounded()))°")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(palette.text)
                    Text(weather.cityName)
                        .font(.system(size: 16))
                        .foregroundColor(palette.text)
                        .lineLimit(1)
                }
                Text("\(weather.conditionText) · ощущается как \(Int(weather.apparentTempC.rounded()))°")
                    .font(.system(size: 14))
                    .foregroundColor(palette.secondaryText)
                    .multilineTextAlignment(.center)
                Text("\(weather.locationSource) · \(weather.dataSource)")
                    .font(.system(size: 12))
                    .foregroundColor(palette.secondaryText)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func icon(for weather: WeatherForecast) -> String {
        switch weather.weatherCode {
        case 0, 1: return weather.isDay ? "sun.max" : "moon"
        case 2, 3: return "cloud"
        case 45, 48: return "cloud.fog"
        case 71, 73, 75, 77, 85, 86: return "snowflake"
        case 95, 96, 99: return "cloud.bolt"
        default: return "cloud.rain"
        }
    }
}

private struct AddSpeedDialSheet: View {
    var onSave: (String, String, String) -> Void
    var onCancel: () -> Void
    @State private var title = ""
    @State private var url = ""
    @State private var category = "Быстрый доступ"
    @Environment(\.palette) private var palette

    var body: some View {
        NavigationView {
            Form {
                TextField("Название сайта", text: $title)
                TextField("Адрес (URL)", text: $url)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                TextField("Категория", text: $category)
            }
            .navigationTitle("Новая карточка быстрого доступа")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") {
                        if !url.trimmingCharacters(in: .whitespaces).isEmpty {
                            onSave(title, url, category)
                        }
                    }
                }
            }
        }
    }
}
