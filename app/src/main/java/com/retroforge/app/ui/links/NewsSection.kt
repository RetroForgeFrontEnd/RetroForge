package com.retroforge.app.ui.links

import com.retroforge.app.R
import androidx.compose.ui.res.stringResource
import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Links → Retro news: the latest from [RetroNews]'s feeds, fetched when the tab opens if the saved ones are more than
 * a few hours old, and kept for when there's no connection.
 */
@Composable
fun NewsSection(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember { File(context.filesDir, "news/stories.json") }
    var fetched by remember { mutableStateOf<RetroNews.Fetched?>(null) }
    var loading by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf<RetroNews.Kind?>(null) }
    var shown by remember { mutableIntStateOf(6) }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val fresh = runCatching {
                RetroNews.fetch { url -> RetroNews.download(url, "RetroForge/${com.retroforge.app.BuildConfig.VERSION_NAME}") }
            }.getOrNull()
            if (fresh != null && fresh.stories.isNotEmpty()) {
                fetched = fresh
                withContext(Dispatchers.IO) { runCatching { RetroNews.save(file, fresh) } }
            } else if (fresh != null) {
                fetched = (fetched ?: fresh).copy(failed = fresh.failed)
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        fetched = withContext(Dispatchers.IO) { RetroNews.load(file) }
        if (fetched.let { it == null || System.currentTimeMillis() - it.time > RetroNews.FRESH_MS }) refresh()
    }

    Text(stringResource(R.string.news_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
    CardBody(stringResource(R.string.news_body))
    ChipRow {
        FocusChip((if (kind == null) "● " else "") + stringResource(R.string.news_all), onClick = { kind = null; shown = 6 })
        FocusChip((if (kind == RetroNews.Kind.NEWS) "● " else "") + stringResource(R.string.news_news), onClick = { kind = RetroNews.Kind.NEWS; shown = 6 })
        FocusChip((if (kind == RetroNews.Kind.EMULATORS) "● " else "") + stringResource(R.string.news_emulators), onClick = { kind = RetroNews.Kind.EMULATORS; shown = 6 })
        FocusChip((if (kind == RetroNews.Kind.MODDING) "● " else "") + stringResource(R.string.news_modding), onClick = { kind = RetroNews.Kind.MODDING; shown = 6 })
        FocusChip(stringResource(R.string.news_refresh), enabled = !loading, onClick = ::refresh)
    }
    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    val now = fetched
    if (now == null) {
        if (!loading) CardBody(stringResource(R.string.news_none))
        return
    }
    if (now.failed.isNotEmpty()) CardBody(stringResource(R.string.news_failed, now.failed.joinToString()))
    val stories = now.stories.filter { kind == null || it.kind == kind }
    stories.take(shown).forEach { story ->
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(story.link) }) {
            CardTitle(story.title)
            val ago = if (story.time > 0) DateUtils.getRelativeTimeSpanString(story.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() else null
            Text(listOfNotNull(story.source, ago).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            if (story.summary.isNotBlank()) CardBody(story.summary)
        }
    }
    if (stories.size > shown) FocusChip(stringResource(R.string.news_more), onClick = { shown += 10 })
}
