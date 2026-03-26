package com.convoy.androidtranscriber;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

public class ModelListAdapter extends ArrayAdapter<ManageModelsActivity.ModelRow> {
    public ModelListAdapter(@NonNull Context context, @NonNull List<ManageModelsActivity.ModelRow> items) {
        super(context, 0, items);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_model_entry, parent, false);
        }

        ManageModelsActivity.ModelRow row = getItem(position);
        if (row != null) {
            TextView name = view.findViewById(R.id.tvModelName);
            TextView meta = view.findViewById(R.id.tvModelMeta);
            name.setText(row.displayName);
            meta.setText(row.statusLine());
        }
        return view;
    }
}
