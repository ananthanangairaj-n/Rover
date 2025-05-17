package com.example.userdevice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.Instant

class RoverAdapter(
    private var rovers: List<Rover>,
    private val onItemClick: (Rover) -> Unit
) : RecyclerView.Adapter<RoverAdapter.RoverViewHolder>() {

    class RoverViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val roverName: TextView = itemView.findViewById(R.id.roverName)
        val roverPass: TextView = itemView.findViewById(R.id.roverPassword)
        val roverStatus: TextView = itemView.findViewById(R.id.roverStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoverViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_rover_item, parent, false)
        return RoverViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoverViewHolder, position: Int) {
        val rover = rovers[position]
        holder.roverName.text = "Rover Name: ${rover.rovername}"
        holder.roverPass.text = "Phone no: ${rover.roverpassword}"

        val isOnline = rover.heartbeat?.let {
            Instant.now().epochSecond - Instant.parse(it + "Z").epochSecond <= 35
        } ?: false

        holder.roverStatus.text = "Status: ${if (isOnline) "Online" else "Offline"}"
        holder.roverStatus.setTextColor(
            if (isOnline) holder.itemView.context.getColor(android.R.color.holo_green_dark)
            else holder.itemView.context.getColor(android.R.color.holo_red_dark)
        )

        holder.itemView.setOnClickListener { onItemClick(rover) }
    }

    override fun getItemCount(): Int = rovers.size

    fun updateData(newRovers: List<Rover>) {
        rovers = newRovers
        notifyDataSetChanged()
    }
}
