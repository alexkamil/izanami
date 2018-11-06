import React, { Component } from 'react';
import _ from 'lodash';


class SearchResult extends Component {

  state = {
    hover: -1
  };

  selectValue = (i, values) => () => {
    const segments = values.slice(0, i + 1);
    const key = segments.join(":");
    this.props.onSelect(key);
  };

  setHoverIndex = i => e => {
    this.setState({hover: i});
  };

  resetHoverIndex = e => {
    this.setState({hover: -1});
  };

  classOfElt = i => {
    if (i <= this.state.hover) {
      return "keypicker-result-value result-active";
    } else {
      return "keypicker-result-value";
    }
  };

  render() {
    const values = this.props.value.split(":").filter(e => !!e);
    const size = values.length;
    return (
      <div className="keypicker-result-control ">
        <span className="keypicker-multi-value-wrapper btn-group btn-breadcrumb breadcrumb-info">
        {values.map( (part, i) =>
          [
            <div
              className={`${this.classOfElt(i)} btn btn-info`}
              key={`result-${this.props.value}-${i}`}
              onClick={this.selectValue(i, values)}
              onMouseOver={this.setHoverIndex(i)}
              onMouseOut={this.resetHoverIndex}
            >
              <span className="keypicker-result-value-label">{part}</span>
            </div>
          ])}
        </span>
      </div>
    )
  }
}

const keys = {
  tab: 9,
  backspace: 8
}

export class KeyInput extends Component {

  state = {
    key: this.props.value,
    segments: (this.props.value || '').split(":").filter(e => !!e),
    computedValue: this.props.value,
    textValue: '',
    open: false,
    datas: [],
    editedIndex: -1,
  };

  componentDidMount() {
    document.body.addEventListener('keydown', this.keyboard);
    document.body.addEventListener('click', this.handleTouchOutside);
  }

  componentWillUnmount() {
    document.body.removeEventListener('keydown', this.keyboard);
    document.body.removeEventListener('click', this.handleTouchOutside);
  }

  keyboard = e => {
    if (e.keyCode === keys.tab) {
      e.preventDefault();
      if (this.state.textValue) {
        const segments = [...this.state.segments, ...this.state.textValue.split(":").map(s => s.trim()).filter(s => !!s)];
        const key = segments.join(":");
        this.setState({segments, key, textValue: '', computedValue: key});
      }
      this.validateEditedValue();
    } else if (e.keyCode === keys.backspace) {
      if (this.inputRef && this.inputRef === document.activeElement && this.state.textValue === '') {
        e.preventDefault();
        this.editLastSegment()
      }
    }
  };

  validateEditedValue = () => {
    if (this.state.editedValue || this.state.editedIndex) {
      const key = this.state.segments.join(":");
      this.setState({key, computedValue: key, editedValue: null, editedIndex: -1});
      if (this.inputRef) {
        this.inputRef.focus();
      }
    }
  };

  editLastSegment = () => {
    const segments = this.state.segments.slice(0, -1);
    const last = this.state.segments.slice(-1).pop();
    const key = segments.join(":");
    this.props.onChange(this.state.computedValue);
    this.setState({segments, key, computedValue: key, textValue: last})
  };

  computeValue = (e) => {
    const v = e.target.value;
    if (v.endsWith(":") || v.endsWith(" ")) {
      const segments = [...this.state.segments, ...v.split(":").map(s => s.trim()).filter(s => !!s)];
      const key = segments.join(":");
      this.setState(
        {segments, key, textValue: '', computedValue: key},
        () => this.search()
      );
      this.props.onChange(key);
      this.search();
    } else {
      const computedValue = this.state.key ? this.state.key + ":" + v.trim() : v.trim();
      this.setState(
        {computedValue, textValue: v},
        () => this.search()
      );
      this.props.onChange(computedValue);
    }
  };

  removeLastSegment = () => {
    const segments = this.state.segments.slice(0, -1);
    const key = segments.join(":");
    this.props.onChange(key);
    this.setState({segments, key, computedValue: key})
  };

  onFocus = () => {
    this.validateEditedValue();
    this.search();
  };

  search = () => {
    if (this.state.computedValue.length > 0) {
      this.props.search(this.state.computedValue + "*")
        .then(datas => {
          _.sortBy(datas);
          this.setState({datas, open:true})
        })
    } else {
      this.setState({open:false})
    }
  };

  handleTouchOutside = (event) => {
    if (this.inputRef && this.inputRef.contains(event.target)) {
      this.setState({open:!this.state.open});
    } else if (this.wrapper && !this.wrapper.contains(event.target)) {
      this.setState({open:false});
    }
  };

  selectValue = (key) => {
    this.setState({segments: key.split(":"), key, textValue: '', computedValue: key, datas: [], open: false});

    if (this.inputRef) {
      this.inputRef.focus();
    }
    this.props.onChange(key);
  };

  copyToClipboard = e => {
      const text = this.state.key;

      const textArea = document.createElement("textarea");
      textArea.value = text;
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();

      try {
          document.execCommand('copy');
      } catch (err) {}
      document.body.removeChild(textArea);
  };

  setEditedIndex = (i,text) => e => {
    if (this.state.segments.length - 1 === i) {
      this.editLastSegment();
    } else {
      this.setState({editedIndex:i, editedValue: text})
    }
  };

  changeEditedValue = i => e => {
    const datas = this.state.segments;
    const text = e.target.value;
    if (text.endsWith(":") || text.endsWith(" ")) {
      const truncated = text.substring(0, text.length - 1);
      datas[i] = truncated;
      this.setState(
        {segments:[...datas], computedValue:datas.join(":"), open:false, editedValue: truncated},
        () => this.validateEditedValue()
      );
    } else {
      datas[i] = text;
      this.setState({segments:[...datas], computedValue:datas.join(":"), open:false, editedValue: text})
    }
  };

  render() {
    const size = this.state.segments.length;
    return (
      <div className="form-group">
        <label htmlFor={`input-${this.props.label}`} className="col-sm-2 control-label">{this.props.label}</label>
        <div className="col-sm-10">

          <div className="keypicker keypicker--multi" ref={ref => this.wrapper = ref}>
            <div className="keypicker-control">

              <span className="keypicker-multi-value-wrapper btn-group btn-breadcrumb breadcrumb-info">
                {this.state.segments.map( (part,i) => {
                  if (i === this.state.editedIndex) {
                    return (
                      <span className="btn btn-info keypicker-value" style={{ marginLeft: '0px' }} key={`value-${i}`}>
                        <input
                          autoFocus="true"
                          type="text"
                          ref={e => e && e.setSelectionRange(99999, 99999)}
                          className="key-picker-edited-input"
                          size={`${part.length + 3}`}
                          onChange={this.changeEditedValue(i)}
                          value={this.state.editedValue}
                        />
                    </span>
                    );
                  } else {
                    return (
                      <span className="btn btn-info keypicker-value" style={{ marginLeft: '0px' }} key={`value-${i}`} onDoubleClick={this.setEditedIndex(i, part)}>
                        <span>{part}</span>
                          {i === (size - 1) &&
                          <span className="closeKeypicker" onClick={this.removeLastSegment}>x</span>
                          }
                      </span>
                    );
                  }
                })}
                <div className="keypicker-input" style={{marginLeft: '12px', paddingLeft: '12px', overflow: 'hidden'}}>
                  <input
                    type="text"
                    size={`${this.state.textValue.length}`}
                    onChange={this.computeValue}
                    value={this.state.textValue}
                    onFocus={this.onFocus} ref={e => this.inputRef = e}
                    placeholder={"Press space or : to separate key segments"}
                  />
                </div>
              </span>
              <span>
                <button type="button" className="btn btn-small btn-success" title="copy" onClick={this.copyToClipboard}><i className="fa fa-copy"/></button>
              </span>
            </div>
            {this.state.open &&
            <div className="keypicker-menu-outer" >
              {this.state.datas.map((d, i) =>
                <div key={`res-${i}`}>
                  <SearchResult value={d} onSelect={this.selectValue}/>
                </div>
              )}
            </div>
            }
          </div>
        </div>
      </div>
    );
  }
}
