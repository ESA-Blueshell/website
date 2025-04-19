export type QuestionType = 'open' | 'radio' | 'checkbox' | 'description';

export default interface FormQuestionModel {
  prompt: string;
  type: QuestionType;
  options?: string[];
}

export const defaultFormQuestion: FormQuestionModel = {
  prompt: '',
  type: 'description',
  options: undefined,
};
