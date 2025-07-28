from fastapi import FastAPI
import base64

app = FastAPI()

@app.get("/someint")
def someint():
    return {"result": 1}