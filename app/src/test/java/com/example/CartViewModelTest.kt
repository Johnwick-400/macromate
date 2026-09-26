package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DishItem
import com.example.ui.viewmodel.MacroMateViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CartViewModelTest {

    private lateinit var viewModel: MacroMateViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = MacroMateViewModel(app)
    }

    // RED: fails because existing.quantity += 1 mutates the CartItem in-place,
    // so snapshotItem.quantity is already 2 by the time we assert 1.
    @Test
    fun `addItemToCart does not mutate previously observed CartItem references`() {
        val dish = DishItem(id = "d1", name = "Chicken Roll", description = "Tasty", price = 150.0)

        viewModel.addItemToCart(dish)
        val snapshotItem = viewModel.cart.value.first()

        viewModel.addItemToCart(dish)

        assertEquals("Previously observed CartItem must not be mutated", 1, snapshotItem.quantity)
    }

    @Test
    fun `addItemToCart twice gives quantity 2 in a single cart entry`() {
        val dish = DishItem(id = "d1", name = "Chicken Roll", description = "Tasty", price = 150.0)

        viewModel.addItemToCart(dish)
        viewModel.addItemToCart(dish)

        val cart = viewModel.cart.value
        assertEquals(1, cart.size)
        assertEquals(2, cart[0].quantity)
    }

    @Test
    fun `addItemToCart adds different dishes as separate entries`() {
        val d1 = DishItem(id = "d1", name = "Chicken Roll", description = "desc", price = 150.0)
        val d2 = DishItem(id = "d2", name = "Paneer Tikka", description = "desc", price = 120.0)

        viewModel.addItemToCart(d1)
        viewModel.addItemToCart(d2)

        assertEquals(2, viewModel.cart.value.size)
    }

    @Test
    fun `updateCartQuantity to zero removes item`() {
        val dish = DishItem(id = "d1", name = "Chicken Roll", description = "Tasty", price = 150.0)
        viewModel.addItemToCart(dish)

        viewModel.updateCartQuantity("d1", -1)

        assertEquals(0, viewModel.cart.value.size)
    }
}
