import { Component, signal } from '@angular/core';
import { AssistantComponent } from './assistant/assistant.component';

@Component({
  selector: 'app-root',
  imports: [ AssistantComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('agentic-rag-ui');
}
