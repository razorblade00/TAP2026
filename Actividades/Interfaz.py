import customtkinter


def button_callback():
    contenedor = customtkinter.CTkToplevel()
    contenedor.geometry("300x300")


app = customtkinter.CTk()
app.geometry("400x400")
app.title("Ventana Principal")

button_principal = customtkinter.CTkButton(app, text="Abrir Ventana", command=button_callback)
button_principal.pack(padx=20, pady=20)

app.mainloop()
