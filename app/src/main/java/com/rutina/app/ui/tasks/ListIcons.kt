package com.rutina.app.ui.tasks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Íconos que se pueden elegir para una lista de tareas. */
object ListIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "list" to Icons.Filled.FormatListBulleted,
        "home" to Icons.Filled.Home,
        "work" to Icons.Filled.Work,
        "cart" to Icons.Filled.ShoppingCart,
        "school" to Icons.Filled.School,
        "fitness" to Icons.Filled.FitnessCenter,
        "health" to Icons.Filled.LocalHospital,
        "money" to Icons.Filled.AttachMoney,
        "travel" to Icons.Filled.Flight,
        "book" to Icons.Filled.Book,
        "star" to Icons.Filled.Star,
        "heart" to Icons.Filled.Favorite,
        "pets" to Icons.Filled.Pets,
        "car" to Icons.Filled.DirectionsCar,
        "family" to Icons.Filled.FamilyRestroom,
        "idea" to Icons.Filled.Lightbulb,
    )

    fun of(key: String): ImageVector = all.firstOrNull { it.first == key }?.second ?: Icons.Filled.FormatListBulleted
}
