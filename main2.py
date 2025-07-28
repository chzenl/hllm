from fastapi import FastAPI
import requests
import base64
import os
#from dotenv import load_dotenv

#load_dotenv()
app = FastAPI()

@app.get("/somesum/{a}/")
def somesum(a: int):
    response=requests.get(os.getenv("URLSOMEINT")).json()
    #response=requests.get("http://172.18.0.2:8020/someint").json()
    #print(response)
    b=int(response["result"])
    return {"result": a+b}