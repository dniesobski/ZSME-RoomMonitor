package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppPreferences
import com.example.data.FetchedSchoolData
import com.example.data.ScheduleAnalyzer
import com.example.data.ScheduleCacheData
import com.example.data.ScheduleCacheStore
import com.example.data.SettingsDataStore
import com.example.data.TimetableRepository
import com.example.model.BellStatus
import com.example.model.CachedSubstitutionsDay
import com.example.model.ClockTime
import com.example.model.FloorLevelConfig
import com.example.model.FloorLevelMatcher
import com.example.model.ParsedSubstitutions
import com.example.model.PeriodTime
import com.example.model.RoomAvailabilityStatus
import com.example.model.RoomDaySummary
import com.example.model.SubstitutionEntry
import com.example.update.AppUpdateStatus
import com.example.update.GitHubReleaseInfo
import com.example.update.GitHubUpdateManager
import com.example.util.SchoolDateInfo
import com.example.util.TimeHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val FLOOR_ID_OTHER = "__other__"

data class MainUiState(
    val currentTime: ClockTime = TimeHelper.nowClockTime(),
    val currentDate: SchoolDateInfo = TimeHelper.todayDateInfo(),
    val bellStatus: BellStatus = ScheduleAnalyzer.computeBellStatus(
        TimeHelper.todayDateInfo(),
        TimeHelper.nowClockTime(),
        TimeHelper.defaultPeriods
    ),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val cacheNoticeMessage: String? = null,
    val lastUpdatedText: String? = null,
    val timetableUrl: String = SettingsDataStore.DEFAULT_TIMETABLE_URL,
    val substitutionsUrl: String = SettingsDataStore.DEFAULT_SUBSTITUTIONS_URL,
    val autoRefreshMinutes: Int = 5,
    val floorLevels: List<FloorLevelConfig> = SettingsDataStore.DEFAULT_FLOOR_LEVELS,
    val selectedFloorLevelId: String? = null,
    val allKnownRoomNames: List<String> = emptyList(),
    val freeNowRooms: List<RoomDaySummary> = emptyList(),
    val freeAfterNextRooms: List<RoomDaySummary> = emptyList(),
    val occupiedLaterRooms: List<RoomDaySummary> = emptyList(),
    val totalRoomsCount: Int = 0,
    val substitutions: List<SubstitutionEntry> = emptyList(),
    val substitutionsDateHeader: String? = null,
    val substitutionsMatchesToday: Boolean = true,
    val futureSubstitutionsDays: List<CachedSubstitutionsDay> = emptyList(),
    val isUsingCachedTimetable: Boolean = false,
    val isUsingCachedTodaySubstitutions: Boolean = false,
    val cachedTimetableSavedAt: String? = null,
    val appliedSubstitutionsCount: Int = 0,
    val periodSchedule: List<PeriodTime> = TimeHelper.defaultPeriods,
    val searchQuery: String = "",
    val onlyWithSubstitutions: Boolean = false,
    val hideCleanedRooms: Boolean = false,
    val selectedRoom: RoomDaySummary? = null,
    val settingsFeedback: String? = null,
    val currentAppVersion: String = BuildConfig.VERSION_NAME,
    val githubRepo: String = "",
    val autoCheckUpdates: Boolean = true,
    val updateStatus: AppUpdateStatus = AppUpdateStatus.Idle,
    val updateBannerDismissed: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsDataStore(application.applicationContext)
    private val cacheStore = ScheduleCacheStore(application.applicationContext)
    private val repository = TimetableRepository()
    private val updateManager = GitHubUpdateManager()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var cachedData: FetchedSchoolData? = null
    private var activeCache: ScheduleCacheData = ScheduleCacheData()
    private var currentPrefs = AppPreferences()
    private var lastFetchedUrls: Pair<String, String>? = null
    private var hasAutoCheckedUpdates = false
    private var downloadJob: Job? = null

    init {
        observePreferences()
        startClockLoop()
        startAutoRefreshLoop()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            settingsStore.preferencesFlow.collectLatest { prefs ->
                val urlsChanged = lastFetchedUrls != (prefs.timetableUrl to prefs.substitutionsUrl)
                currentPrefs = prefs
                _uiState.update { state ->
                    val validSelectedId = when (state.selectedFloorLevelId) {
                        null, FLOOR_ID_OTHER -> state.selectedFloorLevelId
                        else -> if (prefs.floorLevels.any { it.id == state.selectedFloorLevelId }) {
                            state.selectedFloorLevelId
                        } else {
                            null
                        }
                    }
                    state.copy(
                        timetableUrl = prefs.timetableUrl,
                        substitutionsUrl = prefs.substitutionsUrl,
                        autoRefreshMinutes = prefs.autoRefreshMinutes,
                        floorLevels = prefs.floorLevels,
                        selectedFloorLevelId = validSelectedId,
                        githubRepo = prefs.githubRepo,
                        autoCheckUpdates = prefs.autoCheckUpdates
                    )
                }
                if (urlsChanged) {
                    lastFetchedUrls = prefs.timetableUrl to prefs.substitutionsUrl
                    val today = TimeHelper.todayDateInfo()
                    activeCache = cacheStore.loadAndPrune(
                        timetableUrl = prefs.timetableUrl,
                        substitutionsUrl = prefs.substitutionsUrl,
                        today = today
                    )
                    applyPersistentCacheBeforeNetwork(today)
                    refreshData(isManual = false)
                } else {
                    recomputeFromCache()
                }
                if (!hasAutoCheckedUpdates && prefs.autoCheckUpdates && prefs.githubRepo.isNotBlank()) {
                    hasAutoCheckedUpdates = true
                    checkForUpdates(isManual = false)
                }
            }
        }
    }

    private fun applyPersistentCacheBeforeNetwork(today: SchoolDateInfo) {
        val tt = activeCache.timetable ?: return
        val todaySubs = activeCache.substitutionsForDate(today.isoKey)
        val futureDays = activeCache.futureSubstitutionDays(today.isoKey)
        cachedData = FetchedSchoolData(
            timetable = tt,
            substitutions = ParsedSubstitutions(
                entries = todaySubs?.entries.orEmpty(),
                headerDateText = todaySubs?.displayDateText,
                matchesToday = true
            ),
            timetableFromNetwork = false,
            substitutionsFromNetwork = false
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                isUsingCachedTimetable = true,
                isUsingCachedTodaySubstitutions = todaySubs != null,
                cachedTimetableSavedAt = activeCache.timetableSavedAtText,
                lastUpdatedText = activeCache.timetableSavedAtText,
                futureSubstitutionsDays = futureDays,
                cacheNoticeMessage = if (todaySubs != null) {
                    "Załadowano zapamiętany plan oraz zastępstwa na dziś (${todaySubs.entries.size}) z pamięci podręcznej."
                } else {
                    "Załadowano zapamiętany plan sal z pamięci podręcznej."
                }
            )
        }
        recomputeFromCache(TimeHelper.nowClockTime(), today)
    }

    private fun startClockLoop() {
        viewModelScope.launch {
            var lastMinuteKey = -1
            var lastDateIso = TimeHelper.todayDateInfo().isoKey
            while (true) {
                val now = TimeHelper.nowClockTime()
                val today = TimeHelper.todayDateInfo()
                val minuteKey = today.dayOfMonth * 1440 + now.totalMinutes

                if (today.isoKey != lastDateIso) {
                    lastDateIso = today.isoKey
                    handleDayRollover(now, today)
                    lastMinuteKey = minuteKey
                } else if (minuteKey != lastMinuteKey) {
                    lastMinuteKey = minuteKey
                    recomputeFromCache(now, today)
                } else {
                    _uiState.update { it.copy(currentTime = now, currentDate = today) }
                }
                delay(1000L)
            }
        }
    }

    private suspend fun handleDayRollover(now: ClockTime, newToday: SchoolDateInfo) {
        activeCache = cacheStore.pruneExpired(newToday)
        val cachedForNewToday = activeCache.substitutionsForDate(newToday.isoKey)
        val futureDays = activeCache.futureSubstitutionDays(newToday.isoKey)
        val tt = cachedData?.timetable ?: activeCache.timetable

        if (tt != null) {
            cachedData = FetchedSchoolData(
                timetable = tt,
                substitutions = ParsedSubstitutions(
                    entries = cachedForNewToday?.entries.orEmpty(),
                    headerDateText = cachedForNewToday?.displayDateText,
                    matchesToday = true
                ),
                timetableFromNetwork = false,
                substitutionsFromNetwork = false
            )
            _uiState.update {
                it.copy(
                    futureSubstitutionsDays = futureDays,
                    isUsingCachedTodaySubstitutions = cachedForNewToday != null,
                    cacheNoticeMessage = if (cachedForNewToday != null) {
                        "Nowy dzień: wyczyszczono stare zastępstwa i aktywowano zapamiętane na dziś (${cachedForNewToday.entries.size})."
                    } else {
                        "Nowy dzień: wyczyszczono stare zastępstwa z pamięci."
                    }
                )
            }
            recomputeFromCache(now, newToday)
        }
        refreshData(isManual = false)
    }

    private fun startAutoRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                val minutes = currentPrefs.autoRefreshMinutes
                if (minutes > 0) {
                    delay(minutes * 60_000L)
                    if (!_uiState.value.isLoading && !_uiState.value.isRefreshing) {
                        refreshData(isManual = false)
                    }
                } else {
                    delay(30_000L)
                }
            }
        }
    }

    fun refreshData(isManual: Boolean = true) {
        viewModelScope.launch {
            val hasCache = cachedData != null
            _uiState.update {
                it.copy(
                    isLoading = !hasCache,
                    isRefreshing = hasCache || isManual,
                    errorMessage = null,
                    settingsFeedback = null
                )
            }

            val today = TimeHelper.todayDateInfo()
            val fallbackTimetable = cachedData?.timetable ?: activeCache.timetable
            val result = repository.fetchAll(
                timetableUrl = currentPrefs.timetableUrl,
                substitutionsUrl = currentPrefs.substitutionsUrl,
                today = today,
                fallbackTimetable = fallbackTimetable
            )

            result.fold(
                onSuccess = { fetched ->
                    val now = TimeHelper.nowClockTime()
                    val nowText = now.formattedHms()

                    if (fetched.timetableFromNetwork) {
                        activeCache = cacheStore.saveTimetable(
                            timetableUrl = currentPrefs.timetableUrl,
                            timetable = fetched.timetable,
                            savedAtText = nowText,
                            today = today
                        )
                    } else {
                        activeCache = cacheStore.pruneExpired(today)
                    }

                    val parsedByDate = fetched.substitutionsByDate
                    if (parsedByDate != null) {
                        activeCache = cacheStore.mergeSubstitutions(
                            substitutionsUrl = currentPrefs.substitutionsUrl,
                            parsed = parsedByDate,
                            savedAtText = nowText,
                            today = today
                        )
                    }

                    val cachedToday = activeCache.substitutionsForDate(today.isoKey)
                    val futureDays = activeCache.futureSubstitutionDays(today.isoKey)

                    val todayWasInNetworkFetch = parsedByDate != null && (
                        parsedByDate.detectedDatesIso.contains(today.isoKey) ||
                            (parsedByDate.detectedDatesIso.isEmpty() && parsedByDate.unassociatedEntries.isNotEmpty())
                        )

                    val usingCachedTodaySubs = !todayWasInNetworkFetch && cachedToday != null
                    val effectiveTodaySubs = when {
                        cachedToday != null -> ParsedSubstitutions(
                            entries = cachedToday.entries,
                            headerDateText = cachedToday.displayDateText,
                            matchesToday = true
                        )
                        else -> fetched.substitutions
                    }

                    cachedData = fetched.copy(substitutions = effectiveTodaySubs)

                    val cacheNotice = buildCacheNotice(
                        today = today,
                        parsedByDate = parsedByDate,
                        cachedTodayCount = cachedToday?.entries?.size,
                        usingCachedTodaySubs = usingCachedTodaySubs,
                        futureDays = futureDays,
                        timetableFromNetwork = fetched.timetableFromNetwork
                    )

                    val combinedWarning = listOfNotNull(
                        fetched.timetableWarning,
                        fetched.substitutionsWarning
                    ).joinToString("\n").takeIf { it.isNotBlank() }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null,
                            warningMessage = combinedWarning,
                            cacheNoticeMessage = cacheNotice,
                            lastUpdatedText = nowText,
                            futureSubstitutionsDays = futureDays,
                            isUsingCachedTimetable = !fetched.timetableFromNetwork,
                            isUsingCachedTodaySubstitutions = usingCachedTodaySubs,
                            cachedTimetableSavedAt = activeCache.timetableSavedAtText,
                            settingsFeedback = if (isManual) {
                                buildString {
                                    append("Pobrano ${fetched.timetable.rooms.size} sal.")
                                    append(" Zastępstwa na dziś: ${effectiveTodaySubs.entries.size}.")
                                    if (futureDays.isNotEmpty()) {
                                        append(" Zapamiętano na kolejne dni: ${futureDays.sumOf { d -> d.entries.size }}.")
                                    }
                                }
                            } else {
                                null
                            }
                        )
                    }
                    recomputeFromCache(now, today)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.localizedMessage
                                ?: "Nie udało się pobrać planu lekcji. Sprawdź połączenie z internetem oraz link w ustawieniach.",
                            settingsFeedback = if (isManual) {
                                "Błąd połączenia: ${error.localizedMessage ?: "nieznany błąd"}"
                            } else {
                                null
                            }
                        )
                    }
                }
            )
        }
    }

    private fun buildCacheNotice(
        today: SchoolDateInfo,
        parsedByDate: com.example.model.ParsedSubstitutionsByDate?,
        cachedTodayCount: Int?,
        usingCachedTodaySubs: Boolean,
        futureDays: List<CachedSubstitutionsDay>,
        timetableFromNetwork: Boolean
    ): String? {
        if (parsedByDate != null) {
            val todayIso = today.isoKey
            val networkShowsFutureOnly = parsedByDate.detectedDatesIso.isNotEmpty() &&
                !parsedByDate.detectedDatesIso.contains(todayIso) &&
                parsedByDate.detectedDatesIso.any { it > todayIso }

            if (networkShowsFutureOnly) {
                val futureLabel = futureDays.joinToString(", ") {
                    TimeHelper.describeCachedDateLabel(it.isoDate, today)
                }.ifBlank { parsedByDate.headerDateText ?: "kolejny dzień" }

                return if (usingCachedTodaySubs && cachedTodayCount != null) {
                    "Strona zastępstw została już zaktualizowana na: $futureLabel (zapisano w pamięci na jutro). " +
                        "Dzisiejsza zajętość sal nadal korzysta z zapamiętanych zastępstw na dziś ($cachedTodayCount)."
                } else {
                    "Strona zastępstw pokazuje już zapowiedź na: $futureLabel (zapisano w pamięci na kolejny dzień). " +
                        "Brak wcześniejszych zastępstw na dziś w pamięci."
                }
            }
        }

        if (!timetableFromNetwork && usingCachedTodaySubs) {
            return "Tryb pamięci podręcznej — użyto zapisanego planu oraz zapamiętanych zastępstw na dziś."
        }
        return null
    }

    private fun recomputeFromCache(
        now: ClockTime = TimeHelper.nowClockTime(),
        today: SchoolDateInfo = TimeHelper.todayDateInfo()
    ) {
        val data = cachedData
        if (data == null) {
            _uiState.update {
                it.copy(
                    currentTime = now,
                    currentDate = today,
                    bellStatus = ScheduleAnalyzer.computeBellStatus(today, now, it.periodSchedule)
                )
            }
            return
        }

        val analyzed = ScheduleAnalyzer.analyze(
            timetable = data.timetable,
            substitutions = data.substitutions,
            dateInfo = today,
            nowTime = now,
            cleanedRoomsToday = currentPrefs.cleanedRoomsForToday
        )

        val state = _uiState.value
        val filteredRooms = analyzed.rooms.filter { room ->
            matchesFilters(
                room = room,
                query = state.searchQuery,
                floorLevels = currentPrefs.floorLevels,
                selectedLevelId = state.selectedFloorLevelId,
                onlyWithSubs = state.onlyWithSubstitutions,
                hideCleaned = state.hideCleanedRooms
            )
        }

        // Uncleaned rooms stay at top; cleaned rooms are moved to the very bottom
        val freeNow = filteredRooms
            .filter { it.status == RoomAvailabilityStatus.FREE_FOR_TODAY }
            .sortedWith(
                compareBy<RoomDaySummary> { it.isCleanedToday }
                    .thenBy(TimeHelper.roomNameComparator) { it.roomName }
            )

        val freeAfterNext = filteredRooms
            .filter { it.status == RoomAvailabilityStatus.FREE_AFTER_NEXT }
            .sortedWith(
                compareBy<RoomDaySummary> { it.freeFromTime?.totalMinutes ?: Int.MAX_VALUE }
                    .thenBy(TimeHelper.roomNameComparator) { it.roomName }
            )

        val occupiedLater = filteredRooms.filter { it.status == RoomAvailabilityStatus.OCCUPIED_LATER }

        val updatedSelected = state.selectedRoom?.let { prev ->
            analyzed.rooms.firstOrNull { it.roomName == prev.roomName }
        }

        _uiState.update {
            it.copy(
                currentTime = now,
                currentDate = today,
                bellStatus = analyzed.bellStatus,
                allKnownRoomNames = analyzed.rooms.map { r -> r.roomName },
                freeNowRooms = freeNow,
                freeAfterNextRooms = freeAfterNext,
                occupiedLaterRooms = occupiedLater,
                totalRoomsCount = analyzed.rooms.size,
                substitutions = data.substitutions.entries,
                substitutionsDateHeader = data.substitutions.headerDateText,
                substitutionsMatchesToday = data.substitutions.matchesToday,
                appliedSubstitutionsCount = analyzed.appliedSubstitutionsCount,
                periodSchedule = data.timetable.periodTimes.ifEmpty { TimeHelper.defaultPeriods },
                selectedRoom = updatedSelected
            )
        }
    }

    private fun matchesFilters(
        room: RoomDaySummary,
        query: String,
        floorLevels: List<FloorLevelConfig>,
        selectedLevelId: String?,
        onlyWithSubs: Boolean,
        hideCleaned: Boolean
    ): Boolean {
        if (hideCleaned && room.isCleanedToday) return false
        if (onlyWithSubs && !room.becameFreeEarlier && !room.becameFreeLater && room.substitutionNotes.isEmpty()) {
            return false
        }
        if (query.isNotBlank()) {
            val q = query.trim()
            val matchesName = room.roomName.contains(q, ignoreCase = true) ||
                room.roomTitle.contains(q, ignoreCase = true)
            if (!matchesName) return false
        }

        return when (selectedLevelId) {
            null -> true
            FLOOR_ID_OTHER -> floorLevels.none { lvl ->
                FloorLevelMatcher.matchesAnyRule(room.roomName, lvl.rules)
            }
            else -> {
                val lvl = floorLevels.firstOrNull { it.id == selectedLevelId } ?: return true
                FloorLevelMatcher.matchesAnyRule(room.roomName, lvl.rules)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recomputeFromCache()
    }

    fun selectFloorLevel(levelId: String?) {
        _uiState.update { it.copy(selectedFloorLevelId = levelId) }
        recomputeFromCache()
    }

    fun toggleOnlyWithSubstitutions() {
        _uiState.update { it.copy(onlyWithSubstitutions = !it.onlyWithSubstitutions) }
        recomputeFromCache()
    }

    fun toggleHideCleanedRooms() {
        _uiState.update { it.copy(hideCleanedRooms = !it.hideCleanedRooms) }
        recomputeFromCache()
    }

    fun openRoomDetails(room: RoomDaySummary?) {
        _uiState.update { it.copy(selectedRoom = room) }
    }

    fun toggleRoomCleaned(roomName: String) {
        viewModelScope.launch {
            settingsStore.toggleRoomCleaned(roomName)
        }
    }

    fun clearCleanedRooms() {
        viewModelScope.launch {
            settingsStore.clearCleanedRooms()
            _uiState.update { it.copy(settingsFeedback = "Wyczyszczono dzisiejsze oznaczenia posprzątanych sal.") }
        }
    }

    fun saveSettings(timetableUrl: String, substitutionsUrl: String, autoRefreshMinutes: Int) {
        viewModelScope.launch {
            val sameUrls = timetableUrl.trim() == currentPrefs.timetableUrl &&
                substitutionsUrl.trim() == currentPrefs.substitutionsUrl
            settingsStore.updateUrls(timetableUrl, substitutionsUrl, autoRefreshMinutes)
            if (sameUrls) {
                refreshData(isManual = true)
            }
        }
    }

    fun resetDefaultUrls() {
        viewModelScope.launch {
            settingsStore.resetDefaultUrls()
            _uiState.update {
                it.copy(settingsFeedback = "Przywrócono domyślne adresy ZSME.")
            }
        }
    }

    fun addFloorLevel(name: String, initialRules: String = "") {
        viewModelScope.launch {
            val rules = initialRules.split(",", ";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            settingsStore.addFloorLevel(name, rules)
        }
    }

    fun renameFloorLevel(levelId: String, newName: String) {
        viewModelScope.launch {
            settingsStore.renameFloorLevel(levelId, newName)
        }
    }

    fun deleteFloorLevel(levelId: String) {
        viewModelScope.launch {
            settingsStore.deleteFloorLevel(levelId)
        }
    }

    fun addRuleToFloorLevel(levelId: String, rule: String) {
        viewModelScope.launch {
            settingsStore.addRuleToLevel(levelId, rule)
        }
    }

    fun removeRuleFromFloorLevel(levelId: String, rule: String) {
        viewModelScope.launch {
            settingsStore.removeRuleFromLevel(levelId, rule)
        }
    }

    fun resetDefaultFloorLevels() {
        viewModelScope.launch {
            settingsStore.resetDefaultFloorLevels()
            _uiState.update {
                it.copy(settingsFeedback = "Przywrócono domyślne poziomy i zakresy sal.")
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            cacheStore.clearAll()
            activeCache = ScheduleCacheData()
            cachedData = null
            _uiState.update {
                it.copy(
                    futureSubstitutionsDays = emptyList(),
                    isUsingCachedTimetable = false,
                    isUsingCachedTodaySubstitutions = false,
                    cachedTimetableSavedAt = null,
                    cacheNoticeMessage = null,
                    settingsFeedback = "Wyczyszczono pamięć podręczną planu i zastępstw. Pobieranie świeżych danych…"
                )
            }
            refreshData(isManual = true)
        }
    }

    fun saveGithubSettings(repoInput: String, autoCheck: Boolean) {
        viewModelScope.launch {
            settingsStore.updateGithubSettings(repoInput, autoCheck)
            val normalized = GitHubUpdateManager.normalizeRepoSlug(repoInput)
            _uiState.update {
                it.copy(
                    settingsFeedback = if (normalized != null) {
                        "Zapisano repozytorium GitHub: $normalized"
                    } else if (repoInput.isBlank()) {
                        "Wyczyszczono adres repozytorium GitHub."
                    } else {
                        "Zapisano wpis, ale format powinien wyglądać jak właściciel/repozytorium."
                    }
                )
            }
        }
    }

    fun checkForUpdates(isManual: Boolean = true, repoOverride: String? = null) {
        viewModelScope.launch {
            val repoInput = (repoOverride ?: currentPrefs.githubRepo).trim()
            if (repoInput.isBlank()) {
                if (isManual) {
                    _uiState.update {
                        it.copy(
                            updateStatus = AppUpdateStatus.Error(
                                "Podaj najpierw repozytorium GitHub (np. uzytkownik/wolne-sale)."
                            )
                        )
                    }
                }
                return@launch
            }

            if (repoOverride != null && repoOverride.trim() != currentPrefs.githubRepo) {
                settingsStore.updateGithubSettings(repoOverride, currentPrefs.autoCheckUpdates)
            }

            _uiState.update { it.copy(updateStatus = AppUpdateStatus.Checking) }

            val result = updateManager.checkForUpdate(
                repoInput = repoInput,
                currentVersion = BuildConfig.VERSION_NAME
            )

            result.fold(
                onSuccess = { outcome ->
                    val release = outcome.latestRelease
                    val nextStatus = when {
                        outcome.isUpdateAvailable && release != null -> {
                            AppUpdateStatus.UpdateAvailable(release)
                        }
                        else -> {
                            AppUpdateStatus.UpToDate(
                                currentVersion = BuildConfig.VERSION_NAME,
                                latestTag = release?.tagName
                            )
                        }
                    }
                    _uiState.update {
                        it.copy(
                            updateStatus = nextStatus,
                            updateBannerDismissed = if (outcome.isUpdateAvailable) false else it.updateBannerDismissed
                        )
                    }
                },
                onFailure = { error ->
                    if (isManual) {
                        _uiState.update {
                            it.copy(
                                updateStatus = AppUpdateStatus.Error(
                                    error.localizedMessage ?: "Nie udało się sprawdzić aktualizacji na GitHubie."
                                )
                            )
                        }
                    } else {
                        _uiState.update { it.copy(updateStatus = AppUpdateStatus.Idle) }
                    }
                }
            )
        }
    }

    fun downloadUpdate(release: GitHubReleaseInfo) {
        val downloadUrl = release.apkDownloadUrl
        if (downloadUrl.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    updateStatus = AppUpdateStatus.Error(
                        "Wydanie ${release.tagName} nie zawiera załączonego pliku .apk. Otwórz stronę wydania w przeglądarce."
                    )
                )
            }
            return
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateStatus = AppUpdateStatus.Downloading(
                        release = release,
                        downloadedBytes = 0L,
                        totalBytes = release.apkSizeBytes ?: -1L
                    )
                )
            }

            val result = updateManager.downloadApk(
                downloadUrl = downloadUrl,
                preferredFileName = release.apkAssetName ?: "wolne-sale-${release.tagName}.apk",
                cacheDir = getApplication<Application>().cacheDir
            ) { downloaded, total ->
                _uiState.update { state ->
                    if (state.updateStatus is AppUpdateStatus.Downloading) {
                        state.copy(
                            updateStatus = AppUpdateStatus.Downloading(
                                release = release,
                                downloadedBytes = downloaded,
                                totalBytes = if (total > 0L) total else (release.apkSizeBytes ?: -1L)
                            )
                        )
                    } else {
                        state
                    }
                }
            }

            result.fold(
                onSuccess = { apkFile ->
                    _uiState.update {
                        it.copy(
                            updateStatus = AppUpdateStatus.ReadyToInstall(
                                release = release,
                                apkFile = apkFile
                            )
                        )
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) {
                        _uiState.update { it.copy(updateStatus = AppUpdateStatus.UpdateAvailable(release)) }
                    } else {
                        _uiState.update {
                            it.copy(
                                updateStatus = AppUpdateStatus.Error(
                                    err.localizedMessage ?: "Nie udało się pobrać pliku APK."
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    fun cancelUpdateDownload() {
        val current = _uiState.value.updateStatus
        downloadJob?.cancel()
        downloadJob = null
        if (current is AppUpdateStatus.Downloading) {
            _uiState.update { it.copy(updateStatus = AppUpdateStatus.UpdateAvailable(current.release)) }
        }
    }

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(updateBannerDismissed = true) }
    }

    fun reportUpdateError(message: String) {
        _uiState.update { it.copy(updateStatus = AppUpdateStatus.Error(message)) }
    }
}
