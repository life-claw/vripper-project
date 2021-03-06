import { Injectable } from '@angular/core';
import { Subject, Observable, BehaviorSubject } from 'rxjs';

@Injectable()
export class LinkCollectorService {

    private _count$: Subject<number> = new BehaviorSubject(0);

    get count(): Observable<number> {
        return this._count$.asObservable();
    }

    setCount(count: number) {
        this._count$.next(count);
    }
}
