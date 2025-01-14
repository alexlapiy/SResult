package com.rasalexman.sresultpresentation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.LiveData
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.rasalexman.sresult.common.extensions.applyIf
import com.rasalexman.sresult.common.extensions.getErrorMessage
import com.rasalexman.sresult.common.extensions.getMessage
import com.rasalexman.sresult.common.typealiases.AnyResultLiveData
import com.rasalexman.sresult.data.dto.SResult
import com.rasalexman.sresultpresentation.extensions.*
import com.rasalexman.sresultpresentation.fragments.IBaseFragment
import com.rasalexman.sresultpresentation.viewModels.IBaseViewModel
import java.lang.ref.WeakReference

abstract class BaseDialogFragment<VM : IBaseViewModel> : AppCompatDialogFragment(),
    IBaseFragment<VM> {

    override val contentView: View?
        get() = this.view

    override var weakContentRef: WeakReference<View>? = null
    override var weakLoadingRef: WeakReference<View>? = null
    override var weakToolbarRef: WeakReference<Toolbar>? = null

    /**
     * Base view model
     */
    override val viewModel: VM? = null

    /**
     * Toolbar title
     */
    override val toolbarTitle: String = ""

    /**
     * Toolbar subtitle
     */
    override val toolbarSubTitle: String = ""

    /**
     * Toolbar title resourceId
     */
    override val toolbarTitleResId: Int? = null

    /**
     * Need to center toolbar title
     */
    override val centerToolbarTitle: Boolean = false

    /**
     * Need to center toolbar title
     */
    override val centerToolbarSubTitle: Boolean = false

    /**
     * Toolbar menu resId
     */
    @MenuRes
    override val toolbarMenuId: Int? = null

    /**
     *
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layoutId, container, false)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showToolbar()
        initLayout()
        addResultLiveDataObservers()
        addErrorLiveDataObservers()
        addNavigateLiveDataObserver()
    }

    /**
     * For Add Custom Toolbar instance as SupportActionBar
     */
    protected open fun showToolbar() {
        toolbarView?.let { toolbar ->
            toolbarMenuId?.let {
                inflateToolBarMenu(toolbar, it)
            }
            initToolbarTitle(toolbar)
        }
    }

    override fun inflateToolBarMenu(toolbar: Toolbar, menuResId: Int) {
        toolbar.inflateMenu(menuResId)
        toolbar.setOnMenuItemClickListener(this)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return true
    }

    protected open fun addNavigateLiveDataObserver() {
        viewModel?.navigationLiveData?.apply(::observeNavigationLiveData)
    }

    /**
     * Add Standard Live data Observers to handler [SResult] event
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun addResultLiveDataObservers() {
        (viewModel?.resultLiveData as? AnyResultLiveData)?.observe(this.viewLifecycleOwner) { result ->
            result.applyIf(!result.isHandled, ::onResultHandler)
        }
    }

    /**
     * Add Standard Live data Observers to handler [SResult] event
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun addErrorLiveDataObservers() {
        (viewModel?.supportLiveData as? AnyResultLiveData)?.observe(this.viewLifecycleOwner) { result ->
            result.applyIf(!result.isHandled, ::onResultHandler)
        }
    }

    /**
     * Observe only [SResult.NavigateResult] live data
     */
    protected open fun observeNavigationLiveData(data: LiveData<SResult.NavigateResult>) {
        onResultChange(data) { result ->
            result.applyIf(!result.isHandled, ::onResultHandler)
        }
    }

    override fun onResultHandler(result: SResult<*>) {
        onBaseResultHandler(result)
    }

    override fun onBackPressed(): Boolean {
        dismiss()
        return false
    }

    override fun onToolbarBackPressed() = Unit
    override fun showEmptyLayout() = Unit
    override fun onNextPressed() = Unit
    override fun showProgress(progress: Int, message: Any?) = Unit
    override fun showNavigationError(e: Exception?, navResId: Int?) = Unit
    override fun showSuccess(result: SResult.Success<*>) = Unit

    /**
     * Navigate by direction [NavDirections]
     */
    override fun navigateTo(direction: NavDirections) {
        this.navigateTo(context, findNavController(), direction)
    }

    /**
     * Navigate by navResId [Int]
     */
    override fun navigateBy(navResId: Int) {
        this.navigateBy(context, findNavController(), navResId)
    }

    /**
     * Navigate back by pop with navResId
     */
    override fun navigatePopTo(navResId: Int?, isInclusive: Boolean) {
        this.navigatePopTo(context, findNavController(), navResId, isInclusive)
    }

    override fun showToast(message: Any?, interval: Int) {
        hideKeyboard()
        hideLoading()
        toast(message, interval)
    }

    /**
     * Show loading state for [SResult.Loading]
     */
    override fun showLoading() {
        hideKeyboard()
        contentViewLayout?.hide()
        loadingViewLayout?.show()
    }

    /**
     * Hide loading when it's needed
     */
    override fun hideLoading() {
        hideKeyboard()
        loadingViewLayout?.hide()
        contentViewLayout?.show()
    }

    protected open fun initLayout() = Unit

    override fun showFailure(error: SResult.AbstractFailure.Failure) {
        this.toast(error.getMessage(), error.interval)
    }

    override fun showAlert(alert: SResult.AbstractFailure.Alert) {
        this.alert(
            message = alert.getMessage(),
            dialogTitle = alert.dialogTitle,
            okTitle = alert.okTitle,
            cancelTitle = alert.cancelTitle,
            cancelHandler = alert.cancelHandler,
            okHandler = alert.okHandler,
            showCancel = alert.cancelHandler != null
        )
    }

    override fun onDestroyView() {
        weakContentRef?.clear()
        weakLoadingRef?.clear()
        weakToolbarRef?.clear()
        toolbarView?.setOnMenuItemClickListener(null)
        this.view.clearView()
        (view as? ViewGroup)?.removeAllViews()
        viewModel?.liveDataToObserve?.forEach { it.removeObservers(this.viewLifecycleOwner) }
        super.onDestroyView()
    }
}