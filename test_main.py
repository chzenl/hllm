from main import Calc

def test_calc():
  c=Calc()
  assert c.add(1,2)==3
