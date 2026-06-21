package `fun`.kirari.hanako.automation

import androidx.annotation.DrawableRes
import `fun`.kirari.hanako.R
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.ProcessingRoute

/**
 * 悬浮球菜单条目配置
 *
 * @param item 菜单项标识
 * @param label 显示文本
 * @param iconRes 图标资源
 * @param isEnabled 是否可点击
 * @param isChecked 开关是否开启
 */
data class BubbleMenuEntry(
    val item: BubbleMenuItem,
    val label: String,
    @DrawableRes val iconRes: Int,
    val isEnabled: (AppSettings) -> Boolean = { true },
    val isChecked: (AppSettings) -> Boolean = { false }
)

/**
 * 悬浮球菜单注册表
 * 集中定义菜单顺序、图标与状态
 */
object BubbleMenuRegistry {
    val entries: List<BubbleMenuEntry> = listOf(
        BubbleMenuEntry(
            item = BubbleMenuItem.ToggleRoute,
            label = "视觉",
            iconRes = R.drawable.ic_bubble_route,
            isChecked = { it.processingRoute == ProcessingRoute.MULTIMODAL_DIRECT }
        ),
        // 联网搜索开关，需要联网搜索 PR 合入后启用
        // BubbleMenuEntry(
        //     item = BubbleMenuItem.ToggleSearch,
        //     label = "联网",
        //     iconRes = R.drawable.ic_bubble_search,
        //     isChecked = { it.webSearch.enabled }
        // ),
        // 语音识别功能暂未实现，暂时屏蔽，后续直接取消注释即可恢复
        // BubbleMenuEntry(
        //     item = BubbleMenuItem.VoiceRecognition,
        //     label = "语音",
        //     iconRes = R.drawable.ic_bubble_mic,
        //     isEnabled = { false },
        //     isChecked = { false }
        // ),
        BubbleMenuEntry(
            item = BubbleMenuItem.Settings,
            label = "设置",
            iconRes = R.drawable.ic_bubble_settings
        )
    )
}
