package ch.olivsoft.android.blindman

import android.util.Log
import android.view.MenuItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

// Constants
private const val LOG_TAG = "BlindManLayout"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    menuItems: Map<MenuItem, (() -> Unit)?>
) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        modifier = Modifier.statusBarsPadding(),
        actions = {
            var showDropDownMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showDropDownMenu = true }) {
                Icon(
                    painter = painterResource(
                        id = R.drawable.menu_24dp_1f1f1f_fill0_wght400_grad0_opsz24
                    ),
                    contentDescription = "Menu Icon"
                )
            }
            DropdownMenu(
                modifier = Modifier.padding(end = 48.dp),
                expanded = showDropDownMenu,
                onDismissRequest = { showDropDownMenu = false },
                offset = DpOffset(
                    0.dp,
                    -TopAppBarDefaults.TopAppBarExpandedHeight
                )
            ) {
                menuItems.keys.forEach {
                    DropdownMenuItem(
                        text = {
                            Text(
                                it.title.toString(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        onClick = {
                            showDropDownMenu = false
                            menuItems[it]!!.invoke()
                        },
                    )
                }
            }
        }
    )
}

@Composable
private fun BlindManAdView(
    modifier: Modifier = Modifier,
    onAdViewCreated: (AdView) -> Unit
) {
    val adSize = AdSize.BANNER
    val adId =
        if (BuildConfig.DEBUG) stringResource(R.string.ad_unit_test_id)
        else stringResource(R.string.ad_unit_id)
    val adRequest = AdRequest.Builder().build()
    AndroidView(
        factory = {
            AdView(it).apply {
                setAdSize(adSize)
                adUnitId = adId
                loadAd(adRequest)
                onAdViewCreated(this)
                Log.d(LOG_TAG, "Ad view created")
            }
        },
        modifier = modifier
            .height(adSize.height.dp)
            .width(adSize.width.dp),
        update = {
            // Somewhat unclear what to do here
            //it.loadAd(adRequest)
            Log.d(LOG_TAG, "Ad view updated")
        }
    )
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun BlindManLayout(
    modifier: Modifier,
    menuItems: MutableMap<MenuItem, (() -> Unit)?> = mutableMapOf(),
    onAdViewCreated: (AdView) -> Unit = {},
    onLayoutCompleted: () -> Unit = {},
    windowSizeClass: WindowSizeClass = WindowSizeClass.calculateFromSize(
        LocalWindowInfo.current.containerDpSize
    ),
) {
    var msg by remember { mutableStateOf("") }
    val bmViewModel: BlindManViewModel = viewModel()
    bmViewModel.messageTextData.observe(LocalLifecycleOwner.current) {
        msg = it
    }

    LaunchedEffect(Unit) {
        Log.d(LOG_TAG, "Layout completed")
        onLayoutCompleted.invoke()
    }

    BlindManDialogs()

    Scaffold(
        topBar = { TopBar(menuItems) },
        modifier = modifier
            .fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Size classes
            when (windowSizeClass.widthSizeClass) {
                WindowWidthSizeClass.Compact -> {
                    // Message text
                    Text(text = msg)
                    // Game field
                    BlindManGameField(Modifier.weight(1f))
                    // Ad view
                    BlindManAdView(
                        modifier = Modifier,
                        onAdViewCreated = onAdViewCreated
                    )
                }

                else -> {
                    BlindManGameField(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The text appears vertically centered according
                        // to the modifier in the Row function
                        Text(
                            text = msg,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 20.dp),
                        )
                        BlindManAdView(
                            modifier = Modifier
                                .padding(end = 20.dp),
                            onAdViewCreated = onAdViewCreated
                        )
                    }
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun BlindManLayoutPreview() {
    BlindManTheme {
        BlindManLayout(Modifier.fillMaxSize())
    }
}
