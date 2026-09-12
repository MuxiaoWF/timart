package com.muxiao.timart.l10n

/**
 * 全量界面文案词表（单语言一份实例）。
 *
 * 选型说明：不用 Android stringResource 限定符体系，原因有二——
 * 1. domain 层是纯 Kotlin（51 个 JVM 单测前提），不能依赖 android 资源；
 * 2. Compose 单 Activity 下快照状态驱动 `LocalStrings` 换实例即可全树换文案，无需重建 Activity。
 *
 * 消费方式：
 * - 组合层：`val L = LocalStrings.current`（MainActivity 根部经 [rememberStrings] 提供）；
 * - 非组合层（VM / Worker / 通知 / 导出）：`stringsFor(RuntimeSettings.resolvedLang)`；
 * - domain 判定文案（条件句 / 判定原因）在 domain/model/unlock/ConditionText 内按 Lang 独立取词。
 *
 * 含参数的模板用 `String.format` 占位符（多参一律位置参数 `%1$s`）。
 */
interface Strings {
    // ---- 通用 ----
    val ok: String
    val confirm: String
    val cancel: String
    val enter: String
    val retry: String
    val skip: String
    val unknown: String
    val keep: String
    val destroy: String
    val done: String
    val continueWord: String
    val untitled: String
    val untitledCapsule: String
    val inProgress: String
    val prevStep: String
    /** 创建页页级返回（退出封存流程回时轨） */
    val exitCreate: String
    val nextToRules: String
    val nextToSeal: String
    val temporarilyNot: String
    val goEnable: String
    val enable: String
    val go: String

    // ---- 底部导航 ----
    val tabTimeTrack: String
    val tabStarLibrary: String
    val tabDustRecords: String
    val tabSettings: String

    // ---- 时轨（首页） ----
    val homeNarrative: String
    val homeEmpty: String
    val previewLatest: String
    val previewUpcoming: String
    val previewReady: String
    val previewOpened: String
    val previewWaiting: String
    val previewProgressFmt: String
    val previewView: String
    val createNew: String
    val readyWhenFmt: String

    // ---- 星库 ----
    val starNarrative: String
    val starEmpty: String
    val starEmptyTag: String
    val starBatchDeleteTitle: String
    val starBatchDeleteBodyFmt: String

    /** 星库批量「归为销毁」确认弹窗（与删除的区别 = 留尘迹档案） */
    val starBatchDestroyTitle: String
    val starBatchDestroyBodyFmt: String

    // ---- 批量选择（星库 / 尘迹共用） ----
    val batchSelectedFmt: String
    val batchSelectAll: String
    val batchDestroy: String
    val batchDelete: String
    val batchCancel: String

    // ---- 尘迹 ----
    val dustNarrative: String
    val dustEmpty: String
    val dustDeleteTitle: String
    val dustDeleteBodyFmt: String
    val dustDeleteRecord: String
    val dustRecordSpanFmt: String
    val dustBatchDeleteTitle: String
    val dustBatchDeleteBodyFmt: String

    // ---- 写下 ----
    val writeTitle: String
    val writeNarrative: String
    val titleLabel: String
    val photoLabel: String
    val noteLabel: String
    val noteHint: String
    val notePlaceholder: String

    // ---- 开启方式（规则） ----
    val rulesTitle: String
    val rulesNarrative: String
    val rulesSub: String
    val rulesNoCondition: String
    val rulesAddMore: String
    val dependRowTitle: String
    val dependRowDesc: String
    val unset: String
    val condFixedDate: String
    val condMinDays: String
    val condWeekDay: String
    val condTimeRange: String
    val condBattery: String
    val condCharging: String
    val condStep: String
    val condNetwork: String
    val condGps: String
    val condWeather: String
    val condTemp: String
    val condSsid: String
    val condStreak: String
    val condExactTime: String
    val condMinMinutes: String
    val condMonthlyDay: String
    val condYearly: String
    val condAwayFrom: String
    val condSunPhase: String
    val condMoonPhase: String
    val condWeatherMetric: String
    val condBeforeAlarm: String
    val condPowerSave: String
    val condSilent: String
    val condHeadphone: String

    /** 扩展条件第二批（金色时刻/农历/流星雨/飞行模式/音乐/已读联动/凝视次数） */
    val condGoldenHour: String
    val condLunarDate: String
    val condMeteorShower: String
    val condAirplane: String
    val condMusic: String
    val condOtherRead: String
    val condViewCount: String

    /** 扩展条件第三批（黑暗中/时区/移动中 + 长按/生物识别/拍照留念） */
    val condAmbientLight: String
    val condTimezoneAway: String
    val condMoving: String
    val condHoldPress: String
    val condBiometric: String
    val condPhotoKeepsake: String

    /** 表单/挑战提示 */
    val ambientLightNote: String
    val movingNote: String
    val biometricFormHint: String
    val photoFormHint: String
    val challengeHoldButton: String
    val challengeBiometricTitle: String
    val challengeBiometricStart: String
    val challengeBiometricCancel: String
    val challengePhotoStart: String
    val challengePhotoNote: String

    /** 农历条件表单：今日农历对照 + 闰月口径说明 */
    val lunarTodayFmt: String
    val lunarLeapNote: String

    /** 流星雨条件：极大期 ±1 天窗口说明 */
    val meteorWindowNote: String

    /** 凝视计数条件：计数口径说明 */
    val viewCountNote: String
    val condMotion: String
    val condCompass: String
    val condAltitude: String

    /** 设备硬件缺失的条件在选择面板中的禁用短注 */
    val condDeviceUnsupported: String

    /** 「后悔药」条件编辑（隐藏入口：长按锁定尘核，每胶囊一次） */
    val condEditTitle: String
    val condEditDesc: String
    val condEditRemove: String
    val condEditNeedOne: String
    val condEditFailed: String
    val condOpenCount: String
    val condOpenStreak: String
    val condLastOpen: String
    val condCapsuleCount: String
    val condOtherUnlocked: String
    val condOtherDestroyed: String
    val condQuestion: String
    val condPuzzle: String
    val condShake: String
    val condFlipHold: String
    val condNfc: String
    val condFormDesc: String
    val condAtLeast: String
    val condAtMost: String
    /** 状态类条件表单的选项文案（按条件定制，替代早期通用"是/否"） */
    val pillOn: String
    val pillOff: String
    val pillConnected: String
    val pillDisconnected: String
    val pillMuted: String
    val pillNotMuted: String
    val pillPlaying: String
    val pillNotPlaying: String

    /** 详情页时间线：状态类条件未满足时附注当前实际状态（%s = 条件句） */
    val condCurrentFmt: String
    val condMotionNote: String
    val condNoOtherCapsule: String
    val condHoldOption: String
    val qaQuestionLabel: String
    val qaAnswerLabel: String
    val qaAnswerNote: String
    val puzzleAnswerLabel: String
    val puzzleAnswerNote: String
    val nfcFormHint: String
    val nfcModeAny: String
    val nfcModePaired: String
    val nfcWriteStart: String
    val nfcWriteWaiting: String
    val nfcWriteSuccess: String
    val nfcWriteRewrite: String
    val nfcWriteFailNoNdef: String
    val nfcWriteFailSize: String
    val nfcWriteFailIo: String
    val nfcPairNote: String
    val challengeNfcHintPaired: String
    val challengeNfcMismatch: String
    val challengeSectionTitle: String
    val challengeKeepScreenOpen: String
    val challengeFaceDownHint: String
    val challengeNfcHint: String
    val catTime: String
    val catDevice: String
    val catNet: String
    val catUsage: String
    val catChallenge: String
    val condPickerTitle: String

    // ---- 满足方式 / 条件卡 ----
    val logicLabel: String
    val logicAndDesc: String
    val logicOrDesc: String
    val logicAndPill: String
    val logicOrPill: String
    val andWord: String
    val orWord: String
    val removeCond: String
    val realConditionLabel: String

    // ---- 依赖选择 ----
    val depSheetDesc: String
    val depCurrentFmt: String
    val depClear: String
    val depDestroyTag: String
    val depEmpty: String
    val depConfirmTitle: String
    val depStillDepend: String

    // ---- 时间条件表单 ----
    val tfFixedTitle: String
    val tfFixedDesc: String
    val tfFixedConfirm: String
    val tfDaysTitle: String
    val tfDaysDescFmt: String
    val tfDaysValueFmt: String
    val tfWeekTitle: String
    val tfWeekDesc: String
    val tfRangeTitle: String
    val tfRangeFmt: String
    val tfRangeCrossFmt: String
    val tfRangeError: String
    val tfFromFmt: String
    val tfToFmt: String
    val tfToCrossFmt: String

    // ---- GPS 条件表单 / 地址解析 ----
    val gfTitle: String
    val gfDesc: String
    val gfRadiusFmt: String
    val gfConfirm: String
    val gfPermTitle: String
    val gfGeocodeDesc: String
    val gfAddrHint: String
    val gfResolve: String
    val gfResolving: String
    val gfNoResult: String
    val gfNoLocation: String
    val gfManualToggle: String
    val mapLat: String
    val mapLng: String
    val mapUseCurrent: String

    // ---- 天气条件表单 ----
    val efTitle: String
    val efCityLockedFmt: String
    val efNeedCity: String

    // ---- 设备条件表单 ----
    val dfBatteryTitle: String
    val dfBatteryDesc: String
    val dfAbove: String
    val dfBelow: String
    val dfRange: String
    val dfRangeMinFmt: String
    val dfRangeMaxFmt: String
    val dfRangeInvalid: String
    val dfAboveValueFmt: String
    val dfBelowValueFmt: String
    val dfChargingTitle: String
    val dfChargingDesc: String
    val dfChargingPill: String
    val dfNotChargingPill: String
    val dfStepTitle: String
    val dfStepDesc: String
    val dfStepValueFmt: String

    /** 步数条件可选上限滑条标签 */
    val dfStepMaxValueFmt: String
    val dfNetTitle: String
    val dfNetDesc: String

    // ---- 气温 / Wi-Fi / 连续步数 表单 ----
    val tfTitle: String
    val tfDesc: String
    val tfAboveFmt: String
    val tfBelowFmt: String
    val tfRangeMinFmt: String
    val tfRangeMaxFmt: String
    val sfTitle: String
    val sfDesc: String
    val sfHint: String
    val sfAdd: String
    val sfNeedOne: String

    /** SSID 表单：一键读取当前连接的 Wi-Fi 名称 */
    val sfUseCurrent: String

    /** SSID 表单：读不到当前 Wi-Fi（未连接 / 定位服务未开）时的提示 */
    val sfNoCurrent: String

    // ---- 设置 · 权限管理 ----

    /** 权限管理分组卡标题 */
    val setPermCard: String

    /** 各权限行标签 */
    val permNotifLabel: String
    val permFineLocLabel: String
    val permActivityLabel: String
    val permLocServiceLabel: String
    val permLocServiceDesc: String
    val permBatteryLabel: String
    val permBatteryDesc: String

    /** 行尾状态文案 */
    val permGranted: String
    val permNotRequired: String
    val permEnabled: String
    val permDisabled: String

    // ---- 设置 · 关于 ----

    /** 版本行（%1$s = versionName，Kotlin 模板需转义为 %1\$s） */
    val aboutVersionFmt: String

    /** GPL-3.0 许可说明 */
    val aboutLicense: String

    /** 更新与源码仓库行标签 */
    val aboutRepoLabel: String

    /** 关于弹窗：反馈邮箱小注 */
    val aboutFeedbackLabel: String

    // ---- 检查更新（GitHub Releases 占位） ----
    val setCheckUpdate: String
    val setCheckUpdateDesc: String
    val updTitle: String
    val updChecking: String
    val updNewFmt: String
    val updGoDownload: String
    val updUpToDate: String
    val updNotFound: String
    val updNetworkError: String
    val updViewRepo: String

    val kfTitle: String
    val kfDesc: String
    val kfDaysFmt: String
    val kfGoalFmt: String
    val kfStreakNote: String

    /** 步数条件缺陷注明（硬件计步器系统限制；创建页两个步数表单共用） */
    val stepLimitNote: String

    /** 步数条件缺陷注明（短版；详情页时间线步数附注后缀） */
    val stepRestartNote: String

    // ---- 城市与天气 ----
    val weatherCityLabel: String
    val cwDesc: String
    val cwPickCity: String
    val cwLoading: String
    val cwTempFmt: String
    val cwNone: String
    val cwFailed: String
    val cpTitle: String
    val cpSearchHint: String
    val cpNearbyHeader: String
    val cpLocSettingsHint: String
    /** 已授权但拿不到当前定位（定位服务关闭 / 无 lastKnown fix）时「使用当前位置」的行内提示 */
    val cpLocFail: String
    val cpLastUsed: String
    val cpNoMatch: String

    // ---- 确认封存 ----
    val sealTitle: String
    val photosAttachedFmt: String
    val weatherNoneRecorded: String
    val tagsLabel: String
    val tagsDesc: String
    val tagPlaceholder: String
    val tagAdd: String
    val autoDestroyLabel: String
    val autoDestroyDesc: String
    val sealConfirm: String
    val sealConfirmDesc: String
    val sealAskDestroyTitle: String
    val sealAskDestroyBody: String
    val assembleTitle: String
    val assembleDesc: String
    val summarySep: String
    val summaryAndPrefix: String
    val summaryOrPrefix: String
    val summaryDependFmt: String
    val summaryReadDestroy: String
    val summaryImmediate: String

    // ---- 详情（锁定 / 解锁 / 销毁） ----
    val detailTitle: String
    val detailMissing: String
    val destroyAskTitle: String
    val destroyAskBody: String
    val notFinishedReading: String
    val backDestroyAskTitle: String
    val backDestroyAskBody: String
    val posterFail: String
    val posterShare: String
    val lockedTitle: String
    val condAllMet: String
    val condSomeMet: String
    val autoDestroyOnDesc: String
    val autoDestroyOffDesc: String
    val gpsGuideText: String
    val stepGuideText: String
    val contentHidden: String
    val contentHiddenNote: String
    val contentHiddenDetail: String
    val gotIt: String
    val depUnlocked: String
    val depLocked: String
    val depDestroyedPermanent: String
    val depNotFound: String
    val frontCapsuleFallback: String
    val narrIdle1: String
    val narrIdle2: String
    val narrDepDead1: String
    val narrDepDead2: String
    val narrReady1: String
    val narrReady2: String
    val narrOneLeft1: String
    val narrOneLeft2: String
    val condSatisfiedTag: String

    /** 步数条件行附注（%s = 今日步数） */
    val condStepProgressFmt: String
    val unsealSkipHint: String
    val voucherTitle: String
    val createdLabel: String
    val unlockedLabel: String
    val weatherLabel: String
    val readAndDestroy: String
    val readDestroyDesc: String
    val keepInstead: String
    val destroyThisCapsule: String
    val makePoster: String
    val letterNoText: String
    val wShortClear: String
    val wShortCloudy: String
    val wShortFog: String
    val wShortDrizzle: String
    val wShortRain: String
    val wShortSnow: String
    val wShortThunder: String
    val destroyedTitle: String
    val destroyedBody: String
    val destroyedTimeUnknown: String

    // ---- 隐私口令 ----
    val pwSetupTitle: String
    val pwSetupDesc: String
    val pwInputLabelFmt: String
    val pwReinputLabel: String
    val pwAck: String
    val pwSetupFailed: String
    val pwSetupCta: String
    val pwSetupBusy: String
    val pwUnlockTitle: String
    val pwUnlockDesc: String
    val pwUnlockCta: String
    val pwModifyTitle: String
    val pwModifyBusy: String
    val pwPreparing: String
    val pwModifyFailed: String
    val pwModifyDone: String
    val pwOldLabel: String
    val pwNewLabelFmt: String
    val pwMismatch: String
    val pwStartReencrypt: String
    val pwKeepForeground: String

    // ---- 备份 ----
    val bkExportTitle: String
    val bkExportInfo: String
    val bkExportWarn: String
    val bkExportSessionInfo: String
    val bkExportAskPw: String
    val bkAttemptsLeftFmt: String
    val bkPacking: String
    val bkExportDone: String
    val bkWrongPw: String
    val bkExportFailed: String
    val bkImportTitle: String
    val bkImportInfo: String
    val bkImportWarn: String
    val bkImportAskPw: String
    val bkCooldownFmt: String
    val bkVerifying: String
    val bkImportFailed: String
    val bkPickFile: String
    val bkImportDoneFmt: String

    /** 导出/导入完成提示：备份内含本机硬件无法达成的条件（%s = 条件名列表，" · " 分隔） */
    val bkDeviceUnsupportedFmt: String

    // ---- 设置页 ----
    val setTitle: String
    val setIntro: String
    val setMotionCard: String
    val setTierLabel: String
    val setTierDesc: String
    val tierHigh: String
    val tierMid: String
    val tierLow: String
    val setFeedbackCard: String
    val setGyro: String
    val setGyroDesc: String
    val setSpark: String
    val setSparkDesc: String
    val setSound: String
    val setSoundDesc: String
    val setPasswordCard: String
    val setModifyPw: String
    val setModifyPwDesc: String
    val setBackupCard: String
    val setExport: String
    val setExportDesc: String
    val setImport: String
    val setImportDesc: String
    val setInfoCard: String
    val setPerm: String
    val setPermDesc: String
    val setWeatherInfo: String
    val setWeatherInfoDesc: String
    val setAbout: String
    val setAboutDesc: String
    val setLanguageCard: String
    val setLanguageDesc: String
    val langSystem: String
    val langSystemDesc: String
    val langZhHans: String
    val langZhHant: String
    val langEn: String
    val permLocationDesc: String
    val permActivityDesc: String
    val permNotifDesc: String
    val weatherSrcLine: String
    val weatherPrivacyLine: String
    val aboutLine1: String
    val aboutLine2: String

    // ---- 时间前缀（TimeFormatter） ----
    val sealOnPrefix: String
    val destroyedOnPrefix: String

    // ---- 通知 ----
    val notifChannelName: String
    val notifChannelDesc: String
    val notifUnlockTitle: String
    val notifUnlockBody: String

    /** 通知正文（带标题格式化，%s = 胶囊标题） */
    val notifUnlockBodyFmt: String
    val notifGuideTitle: String

    // ---- 海报 ----
    val posterLongBodyNote: String
    val posterBrand: String
    val posterBrandLine: String

    // ---- 错误与异常消息（经弹窗 / Toast 露出） ----
    val errPwMinFmt: String
    val errNewPwMinFmt: String
    val errPwNotUnlockedEnc: String
    val errPwNotUnlockedDec: String
    val errDecryptFailed: String
    val errOldPwWrong: String
    val errSessionKeyLost: String
    val errImageLocked: String
    val errPwNotSetOrUnlocked: String
    val errNotUnlockedRead: String
    val errContentGone: String
    val depErrSelf: String
    val depErrNotFound: String
    val depErrDestroyed: String
    val depErrCycle: String
    val depErrReadDestroy: String
    val errPwNotUnlockedSeal: String
    val errSealFailed: String
    val dvPwWrong: String
    val dvDecryptFailedFmt: String
    val dvCiphertextCorrupt: String
    val dvContentUnreadable: String
    val bkErrNoPw: String
    val bkErrNoVerifier: String
    val bkErrPwParams: String
    val bkErrKdfFmt: String
    val bkErrKdfUnsupported: String
    val bkErrWrongPw: String
    val bkErrSessionLocked: String
    val bkErrZipFmt: String
    val bkErrIo: String
    val bkErrNotTimart: String
    val bkErrNewerVersion: String
    val bkErrMaterialParse: String
    val bkErrWrongPwOrCorrupt: String
    val bkErrDtoParse: String
    val bkErrCapsuleParse: String
    val bkErrContentVerifyFmt: String
    val bkErrReadFile: String
    val bkErrReadFailFmt: String
    val bkErrFormat: String
}