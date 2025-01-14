package com.rasalexman.sresultpresentation.databinding

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.navigation.NavDirections
import androidx.navigation.findNavController
import com.rasalexman.easyrecyclerbinding.createBinding
import com.rasalexman.sresult.common.extensions.applyIf
import com.rasalexman.sresult.common.extensions.loggE
import com.rasalexman.sresult.common.extensions.or
import com.rasalexman.sresult.common.typealiases.AnyResult
import com.rasalexman.sresult.common.typealiases.AnyResultLiveData
import com.rasalexman.sresult.data.dto.SResult
import com.rasalexman.sresultpresentation.BR
import com.rasalexman.sresultpresentation.R
import com.rasalexman.sresultpresentation.extensions.*
import com.rasalexman.sresultpresentation.fragments.IBaseFragment
import com.rasalexman.sresultpresentation.viewModels.BaseViewModel
import com.rasalexman.sresultpresentation.viewModels.CustomViewModelLazy
import java.lang.ref.WeakReference
import kotlin.properties.Delegates

abstract class BaseBindingLayout<VB : ViewDataBinding, VM : BaseViewModel, F : Fragment> :
    FrameLayout,
    LifecycleOwner, IBaseFragment<VM>, IBaseBindingFragment<VB, VM> {

    inline fun <reified VM : BaseViewModel> viewModels(
        noinline fragmentProducer: () -> F = { this.findFragment() }
    ): Lazy<VM> {
        return CustomViewModelLazy(VM::class, fragmentProducer)
    }

    protected open var parentFragmentLifecycle: WeakReference<Lifecycle>? = null

    private val parentLifecycle: Lifecycle
        get() {
            return parentFragmentLifecycle?.get().or {
                try {
                    this.findFragment<F>().viewLifecycleOwner.lifecycle.also {
                        parentFragmentLifecycle = WeakReference(it)
                    }
                } catch (e: Exception) {
                    context.getOwner<LifecycleOwner>().lifecycle
                }
            }
        }

    override var weakContentRef: WeakReference<View>? = null
    override var weakLoadingRef: WeakReference<View>? = null
    override var weakToolbarRef: WeakReference<Toolbar>? = null

    /**
     * Need to attach to parent when inflate data binding view
     */
    open val attachToParent: Boolean = true

    /**
     * View reference getter
     */
    override val contentView: View?
        get() = this

    override var binding: VB by Delegates.notNull<VB>()

    /**
     * Fragment ViewModel instance
     */
    override val viewModel: VM? = null

    constructor(context: Context) : super(context) {
        createLayout(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        createLayout(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        createLayout(context, attrs, defStyleAttr)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        createLayout(context, attrs, defStyleAttr)
    }

    private fun createLayout(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        defStyleRes: Int = 0
    ) {
        val layoutLifecycleOwner = this
        val inflater = LayoutInflater.from(context)
        val view = inflater.createBinding<VB>(layoutId, this, attachToParent).run {
            binding = this
            lifecycleOwner = layoutLifecycleOwner
            root
        }
        applyAdditionalParameters(context, attrs, defStyleAttr, defStyleRes)
        initLayout(view)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.let {
            it.setVariable(BR.vm, viewModel)
            initBinding(it)
        }
        addViewModelBasicObservers()
    }

    override fun onDetachedFromWindow() {
        weakContentRef?.clear()
        weakLoadingRef?.clear()
        weakToolbarRef?.clear()
        parentFragmentLifecycle?.clear()
        binding.unbind()
        val lifecycleOwner = this
        viewModel?.apply {
            resultLiveData?.removeObservers(lifecycleOwner)
            supportLiveData.removeObservers(lifecycleOwner)
            navigationLiveData.removeObservers(lifecycleOwner)
            anyLiveData?.removeObservers(lifecycleOwner)
        }
        clearView()
        viewModel?.liveDataToObserve?.forEach { it.removeObservers(lifecycleOwner) }
        super.onDetachedFromWindow()
    }

    protected open fun applyAdditionalParameters(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) = Unit

    override fun initBinding(binding: VB) = Unit
    protected open fun initLayout(view: View) = Unit
    override fun showSuccess(result: SResult.Success<*>) = Unit
    override fun showAlert(alert: SResult.AbstractFailure.Alert) = Unit
    override fun showFailure(error: SResult.AbstractFailure.Failure) = Unit

    protected open fun addViewModelBasicObservers() {
        viewModel?.let { currentViewModel ->
            addSupportLiveDataObservers(currentViewModel)
            addResultLiveDataObservers(currentViewModel)
            addNavigateLiveDataObserver(currentViewModel)
        }
    }

    /**
     * Add Standard Live data Observers to handler [SResult] event
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun addResultLiveDataObservers(currentVM: BaseViewModel) {
        (currentVM.resultLiveData as? AnyResultLiveData)?.apply(::observeResultLiveData)
    }

    /**
     * Add Standard Live data Observers to handler [SResult] event
     */
    protected open fun addSupportLiveDataObservers(currentVM: BaseViewModel) {
        (currentVM.supportLiveData as? AnyResultLiveData)?.apply(::observeResultLiveData)
    }

    /**
     * Add Standard Live data Observers to handler [SResult.NavigateResult] event
     */
    protected open fun addNavigateLiveDataObserver(currentVM: BaseViewModel) {
        currentVM.navigationLiveData.apply(::observeNavigationLiveData)
    }

    /**
     * Observe only [SResult] live data
     */
    protected open fun observeResultLiveData(data: LiveData<AnyResult>) {
        onResultChange(data) { result ->
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

    /**
     * Base Result handler function
     */
    override fun onResultHandler(result: SResult<*>) {
        onBaseResultHandler(result)
    }

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

    /**
     * When navigation is broke
     */
    override fun showNavigationError(e: Exception?, navResId: Int?) {
        loggE(
            e,
            "There is no navigation direction from ${this::class.java.simpleName} with contentViewLayout id = $navResId"
        )
        showToast(R.string.error_internal, Toast.LENGTH_LONG)
    }

    override fun showToast(message: Any?, interval: Int) {
        hideKeyboard()
        hideLoading()
        context?.toast(message, interval)
    }

    override fun showLoading() {
        hideKeyboard()
        contentViewLayout?.hide()
        loadingViewLayout?.show()
    }

    override fun hideLoading() {
        hideKeyboard()
        loadingViewLayout?.hide()
        contentViewLayout?.show()
    }

    override fun showProgress(progress: Int, message: Any?) = Unit

    /**
     * get view [Lifecycle] from its [Context]
     */
    override fun getLifecycle(): Lifecycle {
        return parentLifecycle
    }

    ///------- UNUSED SECTION ----///
    override val toolbarTitle: String = ""
    override val toolbarTitleResId: Int? = null
    override val toolbarMenuId: Int? = null
    override val toolbarSubTitle: String = ""
    override val centerToolbarTitle: Boolean = false
    override val centerToolbarSubTitle: Boolean = false

    override fun onMenuItemClick(item: MenuItem?): Boolean = true
    override fun onBackPressed(): Boolean = false
    override fun startActivityForResult(intent: Intent?, requestCode: Int) = Unit
    override fun inflateToolBarMenu(toolbar: Toolbar, menuResId: Int) = Unit
    override fun onToolbarBackPressed() = Unit
    override fun showEmptyLayout() = Unit
    override fun onNextPressed() = Unit
}