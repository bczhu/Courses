############################################################
##    FILENAME:   count.py    
##    VERSION:    1.0
##    SINCE:      2014-04-23
##    AUTHOR: 
##        Jimmy Lin (xl5224) - JimmyLin@utexas.edu  
##    DESCRIPTION:
##        For CS363D data mining homework 05
##
############################################################
##    Edited by MacVim
##    Documentation auto-generated by Snippet 
############################################################
import itertools

def query(data, toquery, thredshould=3):
    count = 0
    for datum in data:
        if set(toquery).issubset(datum):
            count += 1
    frequent = count >= thredshould
    global total
    if frequent: total += 1
    print toquery, count, frequent

if __name__ == '__main__':
    global total
    total = 0
    Items = ['a', 'b', 'c', 'd', 'e']
    nTransactions = 10
    data = [set([]) for x in range(nTransactions)]
    data[0].update(['a', 'b', 'd', 'e'])
    data[1].update(['b', 'c', 'd'])
    data[2].update(['a', 'b', 'd', 'e'])
    data[3].update(['a', 'c', 'd', 'e'])
    data[4].update(['b', 'c', 'd', 'e'])
    data[5].update(['b', 'd', 'e'])
    data[6].update(['c', 'd'])
    data[7].update(['a', 'b', 'c'])
    data[8].update(['a', 'd', 'e'])
    data[9].update(['b', 'd'])

    for i in range(len(Items)):
        for x in list(itertools.combinations(Items, i)):
            query(data, x)
    print total
