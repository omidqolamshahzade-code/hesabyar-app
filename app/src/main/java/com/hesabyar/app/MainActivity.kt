package com.hesabyar.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

@Serializable data class Account(val id:String, val name:String, val currency:String, val opening:Long=0)
@Serializable data class Entry(val id:String, val accountId:String, val type:String, val amount:Long,
    val currency:String, val person:String="", val note:String="", val date:Long=System.currentTimeMillis(),
    val transferId:String="")
@Serializable data class Store(val accounts:List<Account> = emptyList(), val entries:List<Entry> = emptyList())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        setContent { MaterialTheme { HesabYar(this) } }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HesabYar(ctx:Context) {
    val prefs= remember { ctx.getSharedPreferences("hesabyar",Context.MODE_PRIVATE) }
    var store by remember {
        mutableStateOf(runCatching { Json.decodeFromString<Store>(prefs.getString("data","{}")!!) }.getOrDefault(Store()))
    }
    fun save(s:Store) { store=s; prefs.edit().putString("data",Json.encodeToString(s)).apply() }
    var tab by remember { mutableStateOf(0) }
    var showAccount by remember { mutableStateOf(false) }
    var showEntry by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("") }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Json.encodeToString(store).toByteArray()) } }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { save(Json.decodeFromString<Store>(it.readText())) } }
    }
    val account=store.accounts.find { it.id==selected }
    fun balance(a:Account):Long = a.opening + store.entries.filter { it.accountId==a.id }.sumOf {
        when(it.type) { "واریز" -> it.amount; "برداشت" -> -it.amount; else -> 0L }
    }
    fun fmt(n:Long)=String.format("%,d",n)
    Scaffold(topBar={ TopAppBar(title={Text("حساب‌یار")}, actions={
        TextButton(onClick={exportFile.launch("hesabyar-backup.json")}) { Text("پشتیبان") }
        TextButton(onClick={importFile.launch(arrayOf("application/json"))}) { Text("بازیابی") }
    }) }, bottomBar={ NavigationBar {
        listOf("داشبورد","حساب‌ها","تراکنش‌ها").forEachIndexed { i,s -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={},label={Text(s)}) }
    }}) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(tab) {
                0 -> {
                    Text("خلاصه حساب‌ها", style=MaterialTheme.typography.headlineSmall)
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text("تعداد حساب‌ها: ${store.accounts.size}")
                        Text("جمع موجودی (نمایش جداگانه بر اساس واحد پول)")
                        listOf("تومان","ریال").forEach { c ->
                            val total=store.accounts.filter{it.currency==c}.sumOf{balance(it)}
                            Text("${fmt(total)} $c", style=MaterialTheme.typography.titleLarge)
                        }
                    } }
                    Text("حساب‌ها", style=MaterialTheme.typography.titleMedium)
                    store.accounts.forEach { a -> Card(onClick={selected=a.id;tab=1}, modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(a.name); Text("${fmt(balance(a))} ${a.currency}") }
                            Text("مشاهده")
                        }
                    } }
                    if(store.accounts.isEmpty()) Text("برای شروع، یک حساب بانکی یا صندوق بسازید.")
                }
                1 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("حساب‌ها",style=MaterialTheme.typography.headlineSmall)
                        Button(onClick={showAccount=true}) { Text("حساب جدید") }
                    }
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(store.accounts) { a ->
                            Card(onClick={selected=a.id}, modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                                Text(a.name,style=MaterialTheme.typography.titleMedium)
                                Text("${fmt(balance(a))} ${a.currency}")
                                Text("موجودی اولیه: ${fmt(a.opening)} ${a.currency}",style=MaterialTheme.typography.bodySmall)
                                if(selected==a.id) Text("انتخاب‌شده",color=MaterialTheme.colorScheme.primary)
                            } }
                        }
                    }
                    if(selected.isNotBlank() && account!=null) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={showEntry=true}) { Text("ثبت واریز/برداشت") }
                        OutlinedButton(onClick={showTransfer=true;}) { Text("انتقال وجه") }
                    }
                }
                else -> {
                    Text("تراکنش‌ها",style=MaterialTheme.typography.headlineSmall)
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(store.entries.sortedByDescending{it.date}) { e ->
                            val a=store.accounts.find{it.id==e.accountId}
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                Text("${e.type}  ${fmt(e.amount)} ${e.currency}",style=MaterialTheme.typography.titleMedium,
                                    color=if(e.type=="واریز") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                Text("${a?.name ?: "حساب حذف‌شده"} • ${e.person}")
                                Text(e.note)
                                Text(SimpleDateFormat("yyyy/MM/dd - HH:mm",Locale.getDefault()).format(Date(e.date)),style=MaterialTheme.typography.bodySmall)
                            } }
                        }
                        if(store.entries.isEmpty()) item { Text("هنوز تراکنشی ثبت نشده است.") }
                    }
                }
            }
        }
    }
    if(showAccount) AccountDialog({showAccount=false}) { name,c,opening ->
        save(store.copy(accounts=store.accounts+Account(UUID.randomUUID().toString(),name,c,opening)))
        showAccount=false
    }
    if(showEntry && account!=null) EntryDialog(account,{showEntry=false}) { type,amount,person,note ->
        save(store.copy(entries=store.entries+Entry(UUID.randomUUID().toString(),account.id,type,amount,account.currency,person,note)))
        showEntry=false
    }
    if(showTransfer && account!=null) TransferDialog(account,store.accounts.filter{it.id!=account.id},{showTransfer=false}) { dest,amount,note ->
        val tid=UUID.randomUUID().toString()
        val debit=Entry(UUID.randomUUID().toString(),account.id,"برداشت",amount,account.currency,"انتقال وجه",note,transferId=tid)
        val credit=Entry(UUID.randomUUID().toString(),dest.id,"واریز",amount,dest.currency,"انتقال از ${account.name}",note,transferId=tid)
        if(account.currency==dest.currency) save(store.copy(entries=store.entries+debit+credit))
        else { /* Cross-currency transfer is disabled until an exchange rate is entered. */ }
        showTransfer=false
    }
}
@Composable fun AccountDialog(close:()->Unit, save:(String,String,Long)->Unit) {
    var name by remember{mutableStateOf("")}; var currency by remember{mutableStateOf("تومان")}; var opening by remember{mutableStateOf("0")}
    AlertDialog(onDismissRequest=close,title={Text("ایجاد حساب")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name,{name=it},label={Text("نام حساب (مثلاً بانک ملت)")})
        Row { listOf("تومان","ریال").forEach { c -> FilterChip(selected=currency==c,onClick={currency=c},label={Text(c)},modifier=Modifier.padding(end=6.dp)) } }
        OutlinedTextField(opening,{opening=it.filter{ch->ch.isDigit()}},label={Text("موجودی اولیه")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
    }},confirmButton={TextButton(onClick={if(name.isNotBlank()){save(name,currency,opening.toLongOrNull()?:0) }}){Text("ذخیره")}},dismissButton={TextButton(onClick=close){Text("انصراف")}})
}
@Composable fun EntryDialog(a:Account,close:()->Unit,save:(String,Long,String,String)->Unit) {
    var type by remember{mutableStateOf("واریز")}; var amount by remember{mutableStateOf("")}; var person by remember{mutableStateOf("")}; var note by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("ثبت تراکنش • ${a.name}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row { listOf("واریز","برداشت").forEach { t -> FilterChip(selected=type==t,onClick={type=t},label={Text(t)},modifier=Modifier.padding(end=8.dp)) } }
        OutlinedTextField(amount,{amount=it.filter{c->c.isDigit()}},label={Text("مبلغ به ${a.currency}")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        OutlinedTextField(person,{person=it},label={Text("نام شخص / مشتری")})
        OutlinedTextField(note,{note=it},label={Text("توضیحات")})
    }},confirmButton={TextButton(onClick={amount.toLongOrNull()?.takeIf{it>0}?.let{save(type,it,person,note)}}){Text("ثبت")}},dismissButton={TextButton(onClick=close){Text("انصراف")}})
}
@Composable fun TransferDialog(src:Account,dests:List<Account>,close:()->Unit,save:(Account,Long,String)->Unit) {
    var destId by remember{mutableStateOf(dests.firstOrNull()?.id.orEmpty())}; var amount by remember{mutableStateOf("")}; var note by remember{mutableStateOf("")}
    val dest=dests.find{it.id==destId}
    AlertDialog(onDismissRequest=close,title={Text("انتقال وجه")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("از ${src.name} (${src.currency})")
        if(dests.isEmpty()) Text("ابتدا حساب دیگری بسازید.")
        else {
            dests.forEach { d -> FilterChip(selected=destId==d.id,onClick={destId=d.id},label={Text("${d.name} • ${d.currency}")}) }
            OutlinedTextField(amount,{amount=it.filter{c->c.isDigit()}},label={Text("مبلغ (${src.currency})")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedTextField(note,{note=it},label={Text("توضیحات")})
            if(dest?.currency!=src.currency) Text("انتقال بین تومان و ریال در این نسخه نیازمند تعیین نرخ تبدیل است و فعلاً ثبت نمی‌شود.",color=MaterialTheme.colorScheme.error)
        }
    }},confirmButton={TextButton(onClick={if(dest!=null && dest.currency==src.currency) amount.toLongOrNull()?.takeIf{it>0}?.let{save(dest,it,note)}}){Text("انتقال")}},dismissButton={TextButton(onClick=close){Text("بستن")}})
}
