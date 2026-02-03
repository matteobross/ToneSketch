package it.progmob.tonesketch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileAdapter(
    private val files: List<File>,
    private val onPlay: (File) -> Unit,
    private val onUpload: (File) -> Unit,
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtFileName)
        val btnUpload: Button = view.findViewById(R.id.btnUpload)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.txtName.text = file.name

        // Click sulla riga -> Play
        holder.itemView.setOnClickListener { onPlay(file) }

        // Click su Analizza -> Upload
        holder.btnUpload.setOnClickListener { onUpload(file) }

        // Click sul Cestino -> Elimina
        holder.btnDelete.setOnClickListener { onDelete(file) }
    }

    override fun getItemCount(): Int = files.size
}