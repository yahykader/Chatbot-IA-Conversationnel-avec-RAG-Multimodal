// features/chat/resolvers/chat.resolver.ts

import { Injectable } from '@angular/core';
import { Resolve } from '@angular/router';
import { Store } from '@ngrx/store';
import { Observable, of } from 'rxjs';
import { take, tap, switchMap } from 'rxjs/operators';
import * as ChatActions from '../store/chat.actions';
import * as ChatSelectors from '../store/chat.selectors';

@Injectable({ providedIn: 'root' })
export class ChatResolver implements Resolve<void> {
  
  constructor(private store: Store) {}
  
  resolve(): Observable<void> {
    return this.store.select(ChatSelectors.selectActiveConversationId).pipe(
      take(1),
      tap(activeId => {
        if (!activeId) {
          this.store.dispatch(ChatActions.createConversation());
        }
      }),
      switchMap(() => of(void 0))
    );
  }
}