package petrov.dmitry.testtask;

import android.app.DatePickerDialog;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import petrov.dmitry.testtask.Utility.AppDataBase;

public class ClientActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor>  {

    private EditText firstName;
    private EditText lastName;
    private EditText middleName;
    private EditText phone;
    private EditText date;
    private ListView listView;
    private SimpleCursorAdapter scAdapter;
    private EditText balance;

    private Button saveButton;

    private SimpleDateFormat sdf;
    private DatePickerDialog datePickerDialog;

    private long id;
    private boolean editable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        id = getIntent().getLongExtra(AppDataBase.COLUMN_ID, -1);

        // Создаем datePickerDialog
        sdf = new SimpleDateFormat("yyyy.MM.dd");
        Calendar calendar = Calendar.getInstance();
        datePickerDialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(year, monthOfYear, dayOfMonth);
                date.setText(sdf.format(calendar.getTime()));
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        // Заполяем поля текущими данными клиента
        Cursor cursor = AppDataBase.getInstance().getClient(id);
        firstName = (EditText) findViewById(R.id.first_name);
        firstName.setText(cursor.getString(cursor.getColumnIndex(AppDataBase.COLUMN_FIRST_NAME)));
        lastName = (EditText) findViewById(R.id.last_name);
        lastName.setText(cursor.getString(cursor.getColumnIndex(AppDataBase.COLUMN_LAST_NAME)));
        middleName = (EditText) findViewById(R.id.middle_name);
        middleName.setText(cursor.getString(cursor.getColumnIndex(AppDataBase.COLUMN_MIDDLE_NAME)));
        phone = (EditText) findViewById(R.id.phone);
        phone.setText(cursor.getString(cursor.getColumnIndex(AppDataBase.COLUMN_PHONE_NUMBER)));
        date = (EditText) findViewById(R.id.date);
        date.setText(cursor.getString(cursor.getColumnIndex(AppDataBase.COLUMN_DATE)));
        balance = (EditText) findViewById(R.id.balance);

        // Кнопка входа
        saveButton = (Button) findViewById(R.id.button_save);
        saveButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                if(editable) {
                    // Проверяем заполнены ли обязательные поля
                    boolean errors = false;
                    if (firstName.getText().toString().isEmpty()) {
                        errors = true;
                        firstName.setError(getResources().getString(R.string.error_required_field));
                    }
                    if (lastName.getText().toString().isEmpty()) {
                        errors = true;
                        lastName.setError(getResources().getString(R.string.error_required_field));
                    }
                    if (phone.getText().toString().isEmpty()) {
                        errors = true;
                        phone.setError(getResources().getString(R.string.error_required_field));
                    }
                    if (date.getText().toString().isEmpty()) {
                        errors = true;
                        date.setError(getResources().getString(R.string.error_required_field));
                    }
                    if (errors) {
                        firstName.requestFocus();
                        return;
                    }

                    // Обновление записи
                    AppDataBase.getInstance().updateClient(id,
                            firstName.getText().toString(),
                            lastName.getText().toString(),
                            middleName.getText().toString(),
                            phone.getText().toString(),
                            date.getText().toString()
                    );
                }

                // Отключаю изменение полей
                setEditable(!editable);

                // Обновляю список транзакций
                getLoaderManager().getLoader(0).forceLoad();
            }
        });

        listView = (ListView) findViewById(R.id.listView);

        // Формируем столбцы сопоставления
        String[] fromDB = new String[]{AppDataBase.COLUMN_DATE, AppDataBase.COLUMN_COST, AppDataBase.COLUMN_ID};
        int[] toView = new int[]{R.id.date, R.id.balance, R.id.transaction_item};
        // Устанавливаем адаптер на listView
        scAdapter = new SimpleCursorAdapter(this, R.layout.transaction_item, null, fromDB, toView, 0);
        listView.setAdapter(scAdapter);
        // Добавляем собственный viewBinder
        SimpleCursorAdapter.ViewBinder viewBinder = new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, final Cursor cursor, final int columnIndex){
                switch (view.getId()) {
                    case R.id.transaction_item:
                        // Меняем видимость и устанавливаем слушатель на кнопку удаления транзакиции
                        view.findViewById(R.id.button_del).setVisibility(editable? View.VISIBLE : View.INVISIBLE);
                        view.findViewById(R.id.button_del).setOnClickListener(editable? new DelClick(cursor.getLong(columnIndex)) : null);
                        return true;
                    case R.id.balance:
                        // Устанавливаем сумму тразакции, так же определяем знак транзакции
                        long cost = cursor.getLong(columnIndex);
                        if(cost > 0) {
                            ((TextView) view).setText(String.format("+%s", String.valueOf(cost)));
                        } else {
                            ((TextView) view).setText(String.valueOf(cost));
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };
        scAdapter.setViewBinder(viewBinder);

        // Создаем лоадер для чтения данных
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Уничтожаем курсорладер
        getLoaderManager().destroyLoader(0);
    }

    // Увеличиываем размер listView в зависимости от кол-ва элементов, дабы не было конфликтов с Scroll'ом
    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null)
            return;

        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.UNSPECIFIED);
        int totalHeight = 0;
        View view = null;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            view = listAdapter.getView(i, view, listView);
            if (i == 0)
                view.setLayoutParams(new ViewGroup.LayoutParams(desiredWidth, Toolbar.LayoutParams.WRAP_CONTENT));

            view.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += view.getMeasuredHeight();
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }

    private void setEditable(boolean editable) {
        this.editable = editable;
        firstName.setEnabled(editable);
        lastName.setEnabled(editable);
        middleName.setEnabled(editable);
        phone.setEnabled(editable);
        date.setEnabled(editable);

        // Устанавливаем слушатели на дату рождения
        if(editable) {
            date.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if(hasFocus) datePickerDialog.show();
                }
            });
            saveButton.setText(getResources().getString(R.string.button_save));
        } else {
            date.setOnFocusChangeListener(null);
            saveButton.setText(getResources().getString(R.string.button_edit));
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int transactionId, Bundle args) {
        return new TransactionsLoader(this, id);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Расчитываем баланс
        BigInteger sum = BigInteger.ZERO;
        if (cursor.moveToFirst())
            do {
                sum = sum.add(BigInteger.valueOf(cursor.getLong(cursor.getColumnIndex(AppDataBase.COLUMN_COST))));
            } while (cursor.moveToNext());
        // Устанавливаем баланс
        balance.setText(sum.toString());

        // Обновляем список записей
        scAdapter.swapCursor(cursor);
        setListViewHeightBasedOnChildren(listView);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        scAdapter.swapCursor(null);
        setListViewHeightBasedOnChildren(listView);
        balance.setText("0");
    }

    // Статический класс курсор лоадера для списка транзакций
    private static class TransactionsLoader extends CursorLoader {

        private long id;

        public TransactionsLoader(Context context, long id) {
            super(context);
            this.id = id;
        }

        @Override
        public Cursor loadInBackground() {
            return AppDataBase.getInstance().getTransactions(id);
        }
    }

    // Модифицированный callback нажатия, для хранения уникальных данных
    private class DelClick implements View.OnClickListener {

        private long id;

        public DelClick(long id) {
            this.id = id;
        }

        @Override
        public void onClick(View view) {
            AppDataBase.getInstance().deleteTransaction(id);
            getLoaderManager().getLoader(0).forceLoad();
        }
    }
}
