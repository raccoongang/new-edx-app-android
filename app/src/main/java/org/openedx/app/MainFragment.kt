package org.openedx.app

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.openedx.app.databinding.FragmentMainBinding
import org.openedx.core.adapter.NavigationFragmentAdapter
import org.openedx.core.config.DashboardConfig
import org.openedx.core.presentation.global.app_upgrade.UpgradeRequiredFragment
import org.openedx.core.presentation.global.viewBinding
import org.openedx.dashboard.presentation.DashboardListFragment
import org.openedx.discovery.presentation.DiscoveryNavigator
import org.openedx.discovery.presentation.DiscoveryRouter
import org.openedx.learn.presentation.LearnFragment
import org.openedx.profile.presentation.profile.ProfileFragment

class MainFragment : Fragment(R.layout.fragment_main) {

    private val binding by viewBinding(FragmentMainBinding::bind)
    private val viewModel by viewModel<MainViewModel>()
    private val router by inject<DiscoveryRouter>()

    private lateinit var adapter: NavigationFragmentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(viewModel)
        setFragmentResultListener(UpgradeRequiredFragment.REQUEST_KEY) { _, _ ->
            binding.bottomNavView.selectedItemId = R.id.fragmentProfile
            viewModel.enableBottomBar(false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViewPager()

        binding.bottomNavView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.fragmentLearn -> {
                    viewModel.logMyCoursesTabClickedEvent()
                    binding.viewPager.setCurrentItem(0, false)
                }

                R.id.fragmentDiscover -> {
                    viewModel.logDiscoveryTabClickedEvent()
                    binding.viewPager.setCurrentItem(1, false)
                }

                R.id.fragmentProfile -> {
                    viewModel.logProfileTabClickedEvent()
                    binding.viewPager.setCurrentItem(2, false)
                }
            }
            true
        }
        // Trigger click event for the first tab on initial load
        binding.bottomNavView.selectedItemId = binding.bottomNavView.selectedItemId

        viewModel.isBottomBarEnabled.observe(viewLifecycleOwner) { isBottomBarEnabled ->
            enableBottomBar(isBottomBarEnabled)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.navigateToDiscovery.collect { shouldNavigateToDiscovery ->
                if (shouldNavigateToDiscovery) {
                    binding.bottomNavView.selectedItemId = R.id.fragmentDiscover
                }
            }
        }

        requireArguments().apply {
            getString(ARG_COURSE_ID).takeIf { it.isNullOrBlank().not() }?.let { courseId ->
                val infoType = getString(ARG_INFO_TYPE)

                if (viewModel.isDiscoveryTypeWebView && infoType != null) {
                    router.navigateToCourseInfo(parentFragmentManager, courseId, infoType)
                } else {
                    router.navigateToCourseDetail(parentFragmentManager, courseId)
                }

                // Clear arguments after navigation
                putString(ARG_COURSE_ID, "")
                putString(ARG_INFO_TYPE, "")
            }
        }
    }

    private fun initViewPager() {
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPager.offscreenPageLimit = 4

        val discoveryFragment = DiscoveryNavigator(viewModel.isDiscoveryTypeWebView).getDiscoveryFragment()
        val dashboardFragment = when (viewModel.dashboardType) {
            DashboardConfig.DashboardType.LIST -> DashboardListFragment()
            DashboardConfig.DashboardType.GALLERY -> LearnFragment()
        }

        adapter = NavigationFragmentAdapter(this).apply {
            addFragment(dashboardFragment)
            addFragment(discoveryFragment)
            addFragment(ProfileFragment())
        }
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false
    }

    private fun enableBottomBar(enable: Boolean) {
        binding.bottomNavView.menu.forEach {
            it.isEnabled = enable
        }
    }

    companion object {
        private const val ARG_COURSE_ID = "courseId"
        private const val ARG_INFO_TYPE = "info_type"
        fun newInstance(courseId: String? = null, infoType: String? = null): MainFragment {
            val fragment = MainFragment()
            fragment.arguments = bundleOf(
                ARG_COURSE_ID to courseId,
                ARG_INFO_TYPE to infoType
            )
            return fragment
        }
    }
}
